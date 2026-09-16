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
