#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cmath>
#include <vector>
#include <queue>
#include <algorithm>

#define LOG_TAG "GrooxTyperNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct PixelPoint {
    int x;
    int y;
    double dist;
    bool operator>(const PixelPoint& other) const {
        return dist > other.dist;
    }
};

extern "C" JNIEXPORT void JNICALL
Java_com_grooxtyper_app_native_NativeEngine_nativeInpaintTelea(
        JNIEnv* env,
        jobject thiz,
        jobject srcBitmap,
        jobject maskBitmap,
        jdouble radius) {

    AndroidBitmapInfo srcInfo, maskInfo;
    void* srcPixels = nullptr;
    void* maskPixels = nullptr;

    if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) < 0 ||
        AndroidBitmap_getInfo(env, maskBitmap, &maskInfo) < 0) {
        LOGE("Failed to get bitmap info");
        return;
    }

    if (srcInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        maskInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmaps must be RGBA_8888");
        return;
    }

    if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) < 0 ||
        AndroidBitmap_lockPixels(env, maskBitmap, &maskPixels) < 0) {
        LOGE("Failed to lock pixels");
        return;
    }

    int width = srcInfo.width;
    int height = srcInfo.height;
    uint32_t* srcPtr = (uint32_t*)srcPixels;
    uint32_t* maskPtr = (uint32_t*)maskPixels;

    // Performa: distMap float (4B) bukan double (8B) → setengah memori,
    // cache lebih ramah untuk tile besar. Radius dijepit agar loop tetangga
    // tidak kuadratik membesar.
    if (radius > 12.0) radius = 12.0;
    if (radius < 1.0) radius = 1.0;

    std::vector<uint8_t> maskState(width * height, 0); // 0: Known, 1: Band, 2: Unknown
    std::vector<float> distMap(width * height, 1e9f);

    std::priority_queue<PixelPoint, std::vector<PixelPoint>, std::greater<PixelPoint>> narrowBand;

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = y * width + x;
            uint32_t maskColor = maskPtr[idx];
            uint8_t maskAlpha = (maskColor >> 24) & 0xFF;
            uint8_t maskRed = maskColor & 0xFF;

            if (maskAlpha > 30 && maskRed > 30) {
                maskState[idx] = 2; // Unknown (to be inpainted)
            } else {
                maskState[idx] = 0; // Known
            }
        }
    }

    int dx[4] = {-1, 1, 0, 0};
    int dy[4] = {0, 0, -1, 1};

    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            int idx = y * width + x;
            if (maskState[idx] == 2) {
                bool nearKnown = false;
                for (int i = 0; i < 4; ++i) {
                    int nx = x + dx[i];
                    int ny = y + dy[i];
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                        if (maskState[ny * width + nx] == 0) {
                            nearKnown = true;
                            break;
                        }
                    }
                }
                if (nearKnown) {
                    maskState[idx] = 1; // Band
                    distMap[idx] = 0.0f;
                    narrowBand.push({x, y, 0.0});
                }
            }
        }
    }

    while (!narrowBand.empty()) {
        PixelPoint curr = narrowBand.top();
        narrowBand.pop();

        int cx = curr.x;
        int cy = curr.y;
        int cIdx = cy * width + cx;

        if (maskState[cIdx] == 0) continue;

        double totalWeight = 0.0;
        double rAcc = 0.0, gAcc = 0.0, bAcc = 0.0, aAcc = 0.0;

        // Gradien citra di titik c untuk bobot isophote ala Telea (garis manga tetap nyambung).
        auto lumAt = [&](int x, int y) -> double {
            x = std::max(0, std::min(width - 1, x));
            y = std::max(0, std::min(height - 1, y));
            uint32_t px = srcPtr[y * width + x];
            double r = (px & 0xFF), g = ((px >> 8) & 0xFF), b = ((px >> 16) & 0xFF);
            return 0.299 * r + 0.587 * g + 0.114 * b;
        };
        double gx_c = (lumAt(cx + 1, cy) - lumAt(cx - 1, cy)) * 0.5;
        double gy_c = (lumAt(cx, cy + 1) - lumAt(cx, cy - 1)) * 0.5;
        double grad_c = std::sqrt(gx_c * gx_c + gy_c * gy_c) + 1e-4;

        int rad = (int)ceil(radius);
        for (int ry = -rad; ry <= rad; ++ry) {
            for (int rx = -rad; rx <= rad; ++rx) {
                if (rx == 0 && ry == 0) continue; // Skip self
                int nx = cx + rx;
                int ny = cy + ry;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    int nIdx = ny * width + nx;
                    if (maskState[nIdx] == 0) { // Known neighbor
                        float dist2 = (float)(rx * rx + ry * ry);
                        float rad2 = (float)(radius * radius);
                        if (dist2 <= rad2 && dist2 > 0.0001f) {
                            // Telea: w = dir * dst * lev (disederhanakan, tanpa sqrt berlebih).
                            // dst = 1/dist^2, lev = 1/(1+|T(c)-T(p)|), dir = | (p-c)·N | / (|p-c|*|N|).
                            float dst = 1.0f / dist2;
                            double lp = lumAt(nx, ny);
                            double lc = lumAt(cx, cy);
                            double lev = 1.0 / (1.0 + std::abs(lc - lp) / 32.0);
                            double len = std::sqrt((double)dist2) + 1e-4;
                            // Normal = gradien tegak lurus isophote; pakai grad_c sebagai proxy.
                            double dir = std::abs((rx * gx_c + ry * gy_c) / (len * grad_c));
                            dir = 0.25 + 0.75 * dir; // jangan nol total agar area datar tetap terisi
                            float weight = (float)(dst * lev * dir);
                            uint32_t px = srcPtr[nIdx];
                            aAcc += ((px >> 24) & 0xFF) * weight;
                            bAcc += ((px >> 16) & 0xFF) * weight;
                            gAcc += ((px >> 8) & 0xFF) * weight;
                            rAcc += (px & 0xFF) * weight;
                            totalWeight += weight;
                        }
                    }
                }
            }
        }

        if (totalWeight > 0.0) {
            uint8_t fa = (uint8_t)std::min(255.0, std::max(0.0, aAcc / totalWeight));
            uint8_t fb = (uint8_t)std::min(255.0, std::max(0.0, bAcc / totalWeight));
            uint8_t fg = (uint8_t)std::min(255.0, std::max(0.0, gAcc / totalWeight));
            uint8_t fr = (uint8_t)std::min(255.0, std::max(0.0, rAcc / totalWeight));
            srcPtr[cIdx] = (fa << 24) | (fb << 16) | (fg << 8) | fr;
        }

        maskState[cIdx] = 0; // Mark as known AFTER computing inpainted color

        for (int i = 0; i < 4; ++i) {
            int nx = cx + dx[i];
            int ny = cy + dy[i];
            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                int nIdx = ny * width + nx;
                if (maskState[nIdx] == 2) {
                    maskState[nIdx] = 1;
                    float nDist = (float)(curr.dist + 1.0);
                    distMap[nIdx] = nDist;
                    narrowBand.push({nx, ny, nDist});
                }
            }
        }
    }

    AndroidBitmap_unlockPixels(env, srcBitmap);
    AndroidBitmap_unlockPixels(env, maskBitmap);
}

/**
 * Navier-Stokes ala Bertalmio-Sapiro-Caselles-Ballester 2001 (versi ringkas
 * untuk mobile): transport isophote iteratif + difusi viskositas kecil.
 * Berbeda dari Telea (satu pass fast-marching): tiap piksel lubang diisi
 * berulang dari tetangga yang PALING SEJAJAR dengan arah isophote (tegak
 * lurus gradien), sehingga garis tepi manga tersambung, bukan blur.
 * Dipakai untuk opsi heal STRUKTUR. Iterations<=0 berarti otomatis dari luas.
 */
extern "C" JNIEXPORT void JNICALL
Java_com_grooxtyper_app_native_NativeEngine_nativeInpaintNS(
        JNIEnv* env,
        jobject thiz,
        jobject srcBitmap,
        jobject maskBitmap,
        jdouble radius,
        jint iterations) {

    AndroidBitmapInfo srcInfo, maskInfo;
    void* srcPixels = nullptr;
    void* maskPixels = nullptr;

    if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) < 0 ||
        AndroidBitmap_getInfo(env, maskBitmap, &maskInfo) < 0) {
        LOGE("NS: Failed to get bitmap info");
        return;
    }

    if (srcInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        maskInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("NS: Bitmaps must be RGBA_8888");
        return;
    }

    if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) < 0 ||
        AndroidBitmap_lockPixels(env, maskBitmap, &maskPixels) < 0) {
        LOGE("NS: Failed to lock pixels");
        return;
    }

    int width = srcInfo.width;
    int height = srcInfo.height;
    uint32_t* srcPtr = (uint32_t*)srcPixels;
    uint32_t* maskPtr = (uint32_t*)maskPixels;
    const int n = width * height;

    if (radius > 12.0) radius = 12.0;
    if (radius < 1.0) radius = 1.0;
    int rad = (int)ceil(radius);
    float rad2 = (float)(radius * radius);

    std::vector<uint8_t> state(n, 0); // 0 known, 1 hole
    for (int i = 0; i < n; ++i) {
        uint32_t m = maskPtr[i];
        if ((((m >> 24) & 0xFF) > 30) && ((m & 0xFF) > 30)) state[i] = 1;
    }

    // Urutan onion-peel (BFS dari batas lubang): luar dulu agar transport
    // selalu punya tetangga bernilai saat diproses.
    std::vector<int> order;
    order.reserve(n);
    std::vector<int> frontier, next;
    {
        const int dx[4] = {-1, 1, 0, 0};
        const int dy[4] = {0, 0, -1, 1};
        std::vector<uint8_t> seen(n, 0);
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                int idx = y * width + x;
                if (state[idx] != 1) continue;
                bool nearKnown = false;
                for (int k = 0; k < 4; ++k) {
                    int nx = x + dx[k], ny = y + dy[k];
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height &&
                        state[ny * width + nx] == 0) { nearKnown = true; break; }
                }
                if (nearKnown) { frontier.push_back(idx); seen[idx] = 1; }
            }
        }
        while (!frontier.empty()) {
            next.clear();
            for (int idx : frontier) {
                order.push_back(idx);
                int x = idx % width, y = idx / width;
                const int dx[4] = {-1, 1, 0, 0};
                const int dy[4] = {0, 0, -1, 1};
                for (int k = 0; k < 4; ++k) {
                    int nx = x + dx[k], ny = y + dy[k];
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    int nIdx = ny * width + nx;
                    if (state[nIdx] == 1 && !seen[nIdx]) { seen[nIdx] = 1; next.push_back(nIdx); }
                }
            }
            frontier.swap(next);
        }
    }
    if (order.empty()) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        AndroidBitmap_unlockPixels(env, maskBitmap);
        return;
    }

    auto lumAt = [&](int x, int y) -> double {
        x = std::max(0, std::min(width - 1, x));
        y = std::max(0, std::min(height - 1, y));
        uint32_t px = srcPtr[y * width + x];
        return 0.299 * (px & 0xFF) + 0.587 * ((px >> 8) & 0xFF) + 0.114 * ((px >> 16) & 0xFF);
    };

    int iters = iterations;
    if (iters <= 0) {
        // Adaptif luas: cukup untuk konvergensi, dibatasi agar interaktif.
        iters = (int)(order.size() / 20000) + 6;
        if (iters < 6) iters = 6;
        if (iters > 40) iters = 40;
    }
    if (iters > 200) iters = 200;

    const double viscosity = 0.12; // difusi kecil penstabil (viskositas NS)
    for (int it = 0; it < iters; ++it) {
        for (int idx : order) {
            int cx = idx % width, cy = idx / width;
            // Gradien citra saat ini (central differences).
            double gx = (lumAt(cx + 1, cy) - lumAt(cx - 1, cy)) * 0.5;
            double gy = (lumAt(cx, cy + 1) - lumAt(cx, cy - 1)) * 0.5;
            double gmag = std::sqrt(gx * gx + gy * gy);
            // Arah isophote (tegak lurus gradien); datar -> difusi murni.
            double tx = 0.0, ty = 0.0;
            if (gmag > 1e-3) { tx = -gy / gmag; ty = gx / gmag; }

            double totalW = 0.0, rA = 0.0, gA = 0.0, bA = 0.0, aA = 0.0;
            for (int ry = -rad; ry <= rad; ++ry) {
                for (int rx = -rad; rx <= rad; ++rx) {
                    if (rx == 0 && ry == 0) continue;
                    int nx = cx + rx, ny = cy + ry;
                    if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                    float dist2 = (float)(rx * rx + ry * ry);
                    if (dist2 > rad2 || dist2 < 0.0001f) continue;
                    double len = std::sqrt((double)dist2) + 1e-4;
                    // Penyelarasan isophote: tetangga searah garis tepi berbobot besar.
                    double align = (tx * rx + ty * ry) / len;
                    align = align * align;
                    double w = (0.15 + 0.85 * align) / (double)dist2;
                    // Transport orde-satu: nilai + gradien proyeksi (ala Bertalmio).
                    int nIdx = ny * width + nx;
                    double gxq = (lumAt(nx + 1, ny) - lumAt(nx - 1, ny)) * 0.5;
                    double gyq = (lumAt(nx, ny + 1) - lumAt(nx, ny - 1)) * 0.5;
                    uint32_t px = srcPtr[nIdx];
                    // Transport orde-satu sepanjang isophote (ala Bertalmio):
                    // nilai tetangga + proyeksi gradiennya ke arah garis tepi.
                    double proj = gxq * rx * tx * tx + gyq * ry * ty * ty;
                    double pa = ((px >> 24) & 0xFF);
                    double pr = (px & 0xFF) + proj;
                    double pg = ((px >> 8) & 0xFF) + proj;
                    double pb = ((px >> 16) & 0xFF);
                    aA += pa * w;
                    rA += pr * w;
                    gA += pg * w;
                    bA += pb * w;
                    totalW += w;
                }
            }
            if (totalW > 0.0) {
                // Viskositas kecil: campur hasil transport dengan nilai lama
                // agar iterasi stabil (difusi ala Navier-Stokes).
                uint8_t fa = (uint8_t)std::min(255.0, std::max(0.0, aA / totalW));
                uint8_t fr = (uint8_t)std::min(255.0, std::max(0.0, rA / totalW));
                uint8_t fg = (uint8_t)std::min(255.0, std::max(0.0, gA / totalW));
                uint8_t fb = (uint8_t)std::min(255.0, std::max(0.0, bA / totalW));
                uint32_t old = srcPtr[idx];
                double oa = ((old >> 24) & 0xFF), or_ = (old & 0xFF);
                double og = ((old >> 8) & 0xFF), ob = ((old >> 16) & 0xFF);
                fa = (uint8_t)(fa * (1.0 - viscosity) + oa * viscosity);
                fr = (uint8_t)(fr * (1.0 - viscosity) + or_ * viscosity);
                fg = (uint8_t)(fg * (1.0 - viscosity) + og * viscosity);
                fb = (uint8_t)(fb * (1.0 - viscosity) + ob * viscosity);
                srcPtr[idx] = ((uint32_t)fa << 24) | ((uint32_t)fb << 16) | ((uint32_t)fg << 8) | fr;
            }
        }
    }

    AndroidBitmap_unlockPixels(env, srcBitmap);
    AndroidBitmap_unlockPixels(env, maskBitmap);
}
