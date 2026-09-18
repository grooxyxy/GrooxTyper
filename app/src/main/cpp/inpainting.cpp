#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cmath>
#include <cstdint>
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
 * Navier-Stokes isophote (Bertalmio 2001, ringkas untuk mobile) — v2:
 * BUG LAMA: tiap iterasi membaca SEMUA tetangga termasuk piksel lubang yang
 * belum diproses (masih berisi warna teks/objek asli) → noda gelap bocor, dan
 * iterasi penuh hingga 24x seluruh lubang → hang di mask besar ("gak jalan").
 * V2: SATU lintasan pengisian onion-peel (BFS dari batas lubang) yang HANYA
 * membaca tetangga yang sudah terisi (state==0), berbobot penyelarasan
 * isophote dari gradien tetangga → garis tepi tersambung tanpa kebocoran.
 * Lalu penghalusan terbatas area lubang (viskositas NS) 2 pass saja.
 * Kompleksitas O(n·r²) sekali, bukan O(iters·n·r²) → mask besar tetap cepat.
 * iterations<=0 = otomatis (2 pass halus).
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

    if (radius < 1.0) radius = 1.0;
    int rad = (int)ceil(radius);
    if (rad > 4) rad = 4; // jendela 9x9 cukup untuk isophote; lebih besar = lambat
    const float rad2 = (float)(rad * rad) + 0.5f;

    std::vector<uint8_t> state(n, 0); // 0 known/terisi, 1 hole
    bool anyHole = false;
    for (int i = 0; i < n; ++i) {
        uint32_t m = maskPtr[i];
        if ((((m >> 24) & 0xFF) > 30) && ((m & 0xFF) > 30)) { state[i] = 1; anyHole = true; }
    }
    if (!anyHole) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        AndroidBitmap_unlockPixels(env, maskBitmap);
        return;
    }

    // Urutan onion-peel (BFS dari batas lubang): luar dulu agar pengisian
    // selalu punya tetangga terisi saat diproses.
    std::vector<int> order;
    order.reserve(n);
    {
        const int dx[4] = {-1, 1, 0, 0};
        const int dy[4] = {0, 0, -1, 1};
        std::vector<uint8_t> seen(n, 0);
        std::vector<int> frontier, next;
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

    // PASS 1 — pengisian: tiap piksel lubang HANYA membaca tetangga yang sudah
    // terisi (state==0). Bobot: penyelarasan isophote (tegak lurus gradien
    // tetangga) × 1/dist² → garis tersambung, warna teks asli tak bocor.
    for (int idx : order) {
        int cx = idx % width, cy = idx / width;
        double totalW = 0.0, rA = 0.0, gA = 0.0, bA = 0.0;
        for (int ry = -rad; ry <= rad; ++ry) {
            int ny = cy + ry;
            if (ny < 0 || ny >= height) continue;
            for (int rx = -rad; rx <= rad; ++rx) {
                if (rx == 0 && ry == 0) continue;
                int nx = cx + rx;
                if (nx < 0 || nx >= width) continue;
                float dist2 = (float)(rx * rx + ry * ry);
                if (dist2 > rad2 || dist2 < 0.0001f) continue;
                int nIdx = ny * width + nx;
                if (state[nIdx] != 0) continue; // hanya yang sudah terisi
                double len = std::sqrt((double)dist2) + 1e-4;
                double gxq = (lumAt(nx + 1, ny) - lumAt(nx - 1, ny)) * 0.5;
                double gyq = (lumAt(nx, ny + 1) - lumAt(nx, ny - 1)) * 0.5;
                double gmag = std::sqrt(gxq * gxq + gyq * gyq);
                double align;
                if (gmag > 1e-3) {
                    double tx = -gyq / gmag, ty = gxq / gmag; // arah isophote
                    align = (tx * rx + ty * ry) / len;
                    align = align * align;
                } else {
                    align = 1.0; // area datar: semua arah setara
                }
                double w = (0.15 + 0.85 * align) / (double)dist2;
                uint32_t px = srcPtr[nIdx];
                rA += (px & 0xFF) * w;
                gA += ((px >> 8) & 0xFF) * w;
                bA += ((px >> 16) & 0xFF) * w;
                totalW += w;
            }
        }
        if (totalW > 0.0) {
            uint8_t fr = (uint8_t)std::min(255.0, std::max(0.0, rA / totalW));
            uint8_t fg = (uint8_t)std::min(255.0, std::max(0.0, gA / totalW));
            uint8_t fb = (uint8_t)std::min(255.0, std::max(0.0, bA / totalW));
            uint32_t old = srcPtr[idx];
            srcPtr[idx] = (old & 0xFF000000u) | ((uint32_t)fb << 16) | ((uint32_t)fg << 8) | fr;
            state[idx] = 0; // kini terisi: tetangga berikutnya boleh membacanya
        }
    }

    // PASS 2 — penghalusan terbatas lubang (difusi viskositas NS): rata-rata
    // 5x5 binomial dicampur 50%, hanya beberapa pass, in-place Gauss-Seidel.
    int iters = iterations > 0 ? std::min((int)iterations, 8) : 2;
    for (int it = 0; it < iters; ++it) {
        for (int idx : order) {
            int cx = idx % width, cy = idx / width;
            int rS = 0, gS = 0, bS = 0, cnt = 0;
            for (int ry = -2; ry <= 2; ++ry) {
                int ny = std::max(0, std::min(height - 1, cy + ry));
                for (int rx = -2; rx <= 2; ++rx) {
                    int nx = std::max(0, std::min(width - 1, cx + rx));
                    uint32_t px = srcPtr[ny * width + nx];
                    rS += (px & 0xFF);
                    gS += ((px >> 8) & 0xFF);
                    bS += ((px >> 16) & 0xFF);
                    cnt++;
                }
            }
            uint32_t old = srcPtr[idx];
            int orr = (old & 0xFF), og = ((old >> 8) & 0xFF), ob = ((old >> 16) & 0xFF);
            uint8_t fr = (uint8_t)((orr + rS / cnt) / 2);
            uint8_t fg = (uint8_t)((og + gS / cnt) / 2);
            uint8_t fb = (uint8_t)((ob + bS / cnt) / 2);
            srcPtr[idx] = (old & 0xFF000000u) | ((uint32_t)fb << 16) | ((uint32_t)fg << 8) | fr;
        }
    }

    AndroidBitmap_unlockPixels(env, srcBitmap);
    AndroidBitmap_unlockPixels(env, maskBitmap);
}

/**
 * Pyramid push-pull inpainting untuk MASK BESAR (opsi heal TEKSTUR/GRADASI
 * dan inpaint seleksi):
 * 1) Turun piramida: tiap level 2x lebih kasar, piksel kasar = rata-rata
 *    berbobot piksel known di bawahnya (lubang ikut "terisi" oleh konteks
 *    luas secara alami) → gradasi panjang tersambung mulus.
 * 2) Level terkasar diisi relaksasi Jacobi dari rata-rata global.
 * 3) Naik piramida: piksel tak dikenal mengambil upsample bilinear dari level
 *    di atasnya; piksel known mempertahankan warna asli (tanpa blur pinggir).
 * 4) Grain halus disintesis dari standar deviasi Laplacian cincin sekitar
 *    lubang → kertas/screentone tidak terlihat "plong" seperti difusi polos.
 * Kompleksitas ~O(1.33n): mask ratusan ribu piksel selesai < 1 detik.
 */
struct PyrLevel {
    int w = 0, h = 0;
    std::vector<float> r, g, b, wt;
};

extern "C" JNIEXPORT void JNICALL
Java_com_grooxtyper_app_native_NativeEngine_nativeInpaintPyramid(
        JNIEnv* env,
        jobject thiz,
        jobject srcBitmap,
        jobject maskBitmap,
        jint maxLevels,
        jdouble grainScale) {

    AndroidBitmapInfo srcInfo, maskInfo;
    void* srcPixels = nullptr;
    void* maskPixels = nullptr;

    if (AndroidBitmap_getInfo(env, srcBitmap, &srcInfo) < 0 ||
        AndroidBitmap_getInfo(env, maskBitmap, &maskInfo) < 0) {
        LOGE("Pyramid: Failed to get bitmap info");
        return;
    }

    if (srcInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        maskInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Pyramid: Bitmaps must be RGBA_8888");
        return;
    }

    if (AndroidBitmap_lockPixels(env, srcBitmap, &srcPixels) < 0 ||
        AndroidBitmap_lockPixels(env, maskBitmap, &maskPixels) < 0) {
        LOGE("Pyramid: Failed to lock pixels");
        return;
    }

    const int width = srcInfo.width;
    const int height = srcInfo.height;
    uint32_t* srcPtr = (uint32_t*)srcPixels;
    uint32_t* maskPtr = (uint32_t*)maskPixels;
    const size_t n = (size_t)width * (size_t)height;

    if (maxLevels < 2) maxLevels = 2;
    if (maxLevels > 8) maxLevels = 8;
    if (grainScale < 0.0) grainScale = 0.0;
    if (grainScale > 3.0) grainScale = 3.0;

    std::vector<uint8_t> hole(n, 0);
    bool anyHole = false;
    for (size_t i = 0; i < n; ++i) {
        uint32_t m = maskPtr[i];
        if ((((m >> 24) & 0xFF) > 30) && ((m & 0xFF) > 30)) { hole[i] = 1; anyHole = true; }
    }
    if (!anyHole || width < 4 || height < 4) {
        AndroidBitmap_unlockPixels(env, srcBitmap);
        AndroidBitmap_unlockPixels(env, maskBitmap);
        return;
    }

    // 1) Bangun piramida turun (downsample ber-coverage).
    std::vector<PyrLevel> pyr;
    pyr.reserve(maxLevels);
    {
        PyrLevel L0;
        L0.w = width; L0.h = height;
        L0.r.resize(n); L0.g.resize(n); L0.b.resize(n); L0.wt.resize(n);
        for (size_t i = 0; i < n; ++i) {
            if (hole[i]) {
                L0.r[i] = 0; L0.g[i] = 0; L0.b[i] = 0; L0.wt[i] = 0;
            } else {
                uint32_t px = srcPtr[i];
                L0.r[i] = (float)(px & 0xFF);
                L0.g[i] = (float)((px >> 8) & 0xFF);
                L0.b[i] = (float)((px >> 16) & 0xFF);
                L0.wt[i] = 1.0f;
            }
        }
        pyr.push_back(std::move(L0));
    }
    while ((int)pyr.size() < maxLevels) {
        const PyrLevel& F = pyr.back();
        if (std::min(F.w, F.h) <= 32) break;
        PyrLevel C;
        C.w = (F.w + 1) / 2;
        C.h = (F.h + 1) / 2;
        const size_t cn = (size_t)C.w * (size_t)C.h;
        C.r.assign(cn, 0); C.g.assign(cn, 0); C.b.assign(cn, 0); C.wt.assign(cn, 0);
        for (int y = 0; y < C.h; ++y) {
            for (int x = 0; x < C.w; ++x) {
                float sw = 0, sr = 0, sg = 0, sb = 0;
                int cnt = 0;
                for (int dy = 0; dy < 2; ++dy) {
                    int fy = std::min(2 * y + dy, F.h - 1);
                    for (int dx = 0; dx < 2; ++dx) {
                        int fx = std::min(2 * x + dx, F.w - 1);
                        size_t fi = (size_t)fy * F.w + fx;
                        float w = F.wt[fi];
                        sw += w;
                        sr += F.r[fi] * w;
                        sg += F.g[fi] * w;
                        sb += F.b[fi] * w;
                        cnt++;
                    }
                }
                size_t ci = (size_t)y * C.w + x;
                if (sw > 1e-4f) {
                    C.r[ci] = sr / sw;
                    C.g[ci] = sg / sw;
                    C.b[ci] = sb / sw;
                    C.wt[ci] = sw / (float)cnt;
                }
            }
        }
        pyr.push_back(std::move(C));
    }
    const int levels = (int)pyr.size();

    // 2) Level terkasar: isi sisa lubang dengan rata-rata global + relaksasi.
    {
        PyrLevel& T = pyr.back();
        const size_t tn = (size_t)T.w * (size_t)T.h;
        std::vector<uint8_t> unk(tn, 0);
        double mr = 0, mg = 0, mb = 0, mw = 0;
        for (size_t i = 0; i < tn; ++i) {
            if (T.wt[i] > 1e-4f) {
                mr += T.r[i] * T.wt[i];
                mg += T.g[i] * T.wt[i];
                mb += T.b[i] * T.wt[i];
                mw += T.wt[i];
            } else {
                unk[i] = 1;
            }
        }
        float gr = 255, gg = 255, gb = 255;
        if (mw > 1e-4) { gr = (float)(mr / mw); gg = (float)(mg / mw); gb = (float)(mb / mw); }
        for (size_t i = 0; i < tn; ++i) {
            if (unk[i]) { T.r[i] = gr; T.g[i] = gg; T.b[i] = gb; }
        }
        std::vector<float> tr(tn), tg(tn), tb(tn);
        for (int pass = 0; pass < 60; ++pass) {
            for (int y = 0; y < T.h; ++y) {
                for (int x = 0; x < T.w; ++x) {
                    size_t i = (size_t)y * T.w + x;
                    int xl = std::max(0, x - 1), xr = std::min(T.w - 1, x + 1);
                    int yu = std::max(0, y - 1), yd = std::min(T.h - 1, y + 1);
                    size_t il = (size_t)y * T.w + xl, ir = (size_t)y * T.w + xr;
                    size_t iu = (size_t)yu * T.w + x, id = (size_t)yd * T.w + x;
                    tr[i] = (T.r[il] + T.r[ir] + T.r[iu] + T.r[id]) * 0.25f;
                    tg[i] = (T.g[il] + T.g[ir] + T.g[iu] + T.g[id]) * 0.25f;
                    tb[i] = (T.b[il] + T.b[ir] + T.b[iu] + T.b[id]) * 0.25f;
                }
            }
            for (size_t i = 0; i < tn; ++i) {
                if (unk[i]) { T.r[i] = tr[i]; T.g[i] = tg[i]; T.b[i] = tb[i]; }
            }
        }
    }

    // 3) Rekonstruksi naik: lubang mengambil upsample bilinear level kasar.
    for (int li = levels - 2; li >= 0; --li) {
        PyrLevel& F = pyr[li];
        const PyrLevel& C = pyr[li + 1];
        for (int y = 0; y < F.h; ++y) {
            for (int x = 0; x < F.w; ++x) {
                size_t i = (size_t)y * F.w + x;
                if (F.wt[i] > 1e-3f) continue; // known: pertahankan asli
                float cx = (x + 0.5f) * (float)C.w / (float)F.w - 0.5f;
                float cy = (y + 0.5f) * (float)C.h / (float)F.h - 0.5f;
                int x0 = (int)std::floor(cx), y0 = (int)std::floor(cy);
                float fx = cx - x0, fy = cy - y0;
                x0 = std::max(0, std::min(C.w - 1, x0));
                y0 = std::max(0, std::min(C.h - 1, y0));
                int x1 = std::max(0, std::min(C.w - 1, x0 + 1));
                int y1 = std::max(0, std::min(C.h - 1, y0 + 1));
                fx = std::max(0.f, std::min(1.f, fx));
                fy = std::max(0.f, std::min(1.f, fy));
                size_t i00 = (size_t)y0 * C.w + x0, i10 = (size_t)y0 * C.w + x1;
                size_t i01 = (size_t)y1 * C.w + x0, i11 = (size_t)y1 * C.w + x1;
                float w00 = (1 - fx) * (1 - fy), w10 = fx * (1 - fy);
                float w01 = (1 - fx) * fy, w11 = fx * fy;
                F.r[i] = C.r[i00] * w00 + C.r[i10] * w10 + C.r[i01] * w01 + C.r[i11] * w11;
                F.g[i] = C.g[i00] * w00 + C.g[i10] * w10 + C.g[i01] * w01 + C.g[i11] * w11;
                F.b[i] = C.b[i00] * w00 + C.b[i10] * w10 + C.b[i01] * w01 + C.b[i11] * w11;
            }
        }
    }

    // 4) Grain: std Laplacian pada cincin (dilatasi lubang 10px, tanpa lubang).
    float stdR = 0, stdG = 0, stdB = 0;
    {
        std::vector<uint8_t> ring(hole);
        std::vector<uint8_t> tmp(n, 0);
        for (int it = 0; it < 10; ++it) {
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    size_t i = (size_t)y * width + x;
                    if (ring[i]) { tmp[i] = 1; continue; }
                    bool near = false;
                    for (int dy = -1; dy <= 1 && !near; ++dy) {
                        int yy = y + dy;
                        if (yy < 0 || yy >= height) continue;
                        for (int dx = -1; dx <= 1; ++dx) {
                            int xx = x + dx;
                            if (xx < 0 || xx >= width) continue;
                            if (ring[(size_t)yy * width + xx]) { near = true; break; }
                        }
                    }
                    tmp[i] = near ? 1 : 0;
                }
            }
            ring.swap(tmp);
        }
        const PyrLevel& F0 = pyr[0];
        double sR = 0, sG = 0, sB = 0;
        long cnt = 0;
        for (int y = 1; y < height - 1; ++y) {
            for (int x = 1; x < width - 1; ++x) {
                size_t i = (size_t)y * width + x;
                if (!ring[i] || hole[i]) continue;
                size_t il = i - 1, ir = i + 1, iu = i - width, id = i + width;
                if (hole[il] || hole[ir] || hole[iu] || hole[id]) continue;
                float lr = 4 * F0.r[i] - F0.r[il] - F0.r[ir] - F0.r[iu] - F0.r[id];
                float lg = 4 * F0.g[i] - F0.g[il] - F0.g[ir] - F0.g[iu] - F0.g[id];
                float lb = 4 * F0.b[i] - F0.b[il] - F0.b[ir] - F0.b[iu] - F0.b[id];
                sR += (double)lr * lr;
                sG += (double)lg * lg;
                sB += (double)lb * lb;
                cnt++;
            }
        }
        if (cnt > 8) {
            // Laplacian noise putih: std_L ≈ σ·sqrt(20) → σ ≈ std_L / 4.47.
            stdR = (float)(std::sqrt(sR / cnt) / 4.47);
            stdG = (float)(std::sqrt(sG / cnt) / 4.47);
            stdB = (float)(std::sqrt(sB / cnt) / 4.47);
            stdR = std::min(40.f, stdR * (float)grainScale);
            stdG = std::min(40.f, stdG * (float)grainScale);
            stdB = std::min(40.f, stdB * (float)grainScale);
        }
    }

    // 5) Tulis balik HANYA piksel lubang: warna hasil + grain deterministik,
    //    alpha asli dipertahankan.
    auto hashNoise = [](int x, int y, int c) -> float {
        uint32_t h = (uint32_t)x * 374761393u + (uint32_t)y * 668265263u + (uint32_t)c * 2246822519u;
        h = (h ^ (h >> 13u)) * 1274126177u;
        h ^= (h >> 16u);
        return ((float)(h & 0xFFFFu) / 32767.5f) - 1.0f;
    };
    const PyrLevel& F0 = pyr[0];
    for (int y = 0; y < height; ++y) {
        for (int x = 0; x < width; ++x) {
            size_t i = (size_t)y * width + x;
            if (!hole[i]) continue;
            int fr = (int)std::lround(F0.r[i] + hashNoise(x, y, 0) * stdR);
            int fg = (int)std::lround(F0.g[i] + hashNoise(x, y, 1) * stdG);
            int fb = (int)std::lround(F0.b[i] + hashNoise(x, y, 2) * stdB);
            fr = std::max(0, std::min(255, fr));
            fg = std::max(0, std::min(255, fg));
            fb = std::max(0, std::min(255, fb));
            uint32_t old = srcPtr[i];
            srcPtr[i] = (old & 0xFF000000u) | ((uint32_t)fb << 16) | ((uint32_t)fg << 8) | (uint32_t)fr;
        }
    }

    AndroidBitmap_unlockPixels(env, srcBitmap);
    AndroidBitmap_unlockPixels(env, maskBitmap);
}
