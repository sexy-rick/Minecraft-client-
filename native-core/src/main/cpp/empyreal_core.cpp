#include <jni.h>

#include <algorithm>
#include <cinttypes>
#include <climits>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <fstream>
#include <mutex>
#include <string>
#include <unistd.h>
#include <vector>

namespace {

struct MemRange {
    uint64_t start;
    uint64_t end;
    bool readable;
};

std::mutex g_mutex;
int g_mem_fd = -1;

constexpr size_t kMaxScanResults = 256;
constexpr size_t kScanChunk = 4u * 1024u * 1024u;

int mem_fd() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_mem_fd < 0) {
        g_mem_fd = open("/proc/self/mem", O_RDONLY | O_CLOEXEC);
    }
    return g_mem_fd;
}

bool read_at(uint64_t addr, void* out, size_t len) {
    if (addr == 0ULL || out == nullptr || len == 0) return false;
    if (addr > static_cast<uint64_t>(INT64_MAX)) return false;
    if (len > static_cast<uint64_t>(INT64_MAX) - addr) return false;

    const int fd = mem_fd();
    if (fd < 0) return false;

    ssize_t got = pread64(fd, out, len, static_cast<off64_t>(addr));
    return got == static_cast<ssize_t>(len);
}

std::vector<std::string> read_maps() {
    std::vector<std::string> lines;
    std::ifstream in("/proc/self/maps");
    if (!in.is_open()) return lines;

    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty()) {
            lines.push_back(std::move(line));
        }
    }
    return lines;
}

bool path_matches_module(const std::string& line, const std::string& name) {
    if (name.empty()) return false;

    size_t pos = 0;
    for (int col = 0; col < 5; ++col) {
        pos = line.find_first_of(" \t", pos);
        if (pos == std::string::npos) return false;
        pos = line.find_first_not_of(" \t", pos);
        if (pos == std::string::npos) return false;
    }

    std::string path = line.substr(pos);
    const std::string deleted_suffix = " (deleted)";
    if (path.size() > deleted_suffix.size() &&
        path.compare(path.size() - deleted_suffix.size(), deleted_suffix.size(), deleted_suffix) == 0) {
        path.resize(path.size() - deleted_suffix.size());
    }

    if (name.find('/') != std::string::npos) {
        return path.find(name) != std::string::npos;
    }

    const size_t slash = path.find_last_of("/!");
    const std::string base = (slash == std::string::npos) ? path : path.substr(slash + 1);
    return base == name;
}

std::vector<MemRange> module_ranges(const std::string& name) {
    std::vector<MemRange> ranges;
    if (name.empty()) return ranges;

    for (const std::string& line : read_maps()) {
        if (!path_matches_module(line, name)) continue;

        unsigned long long s = 0ULL;
        unsigned long long e = 0ULL;
        char perms[8] = {0};

        if (sscanf(line.c_str(), "%llx-%llx %7s", &s, &e, perms) < 3) continue;
        if (s == 0ULL || e <= s) continue;

        ranges.push_back(MemRange{
            static_cast<uint64_t>(s),
            static_cast<uint64_t>(e),
            perms[0] == 'r',
        });
    }
    return ranges;
}

bool module_bounds(const std::string& name, uint64_t* out_start, uint64_t* out_end) {
    const std::vector<MemRange> ranges = module_ranges(name);
    if (ranges.empty()) return false;

    uint64_t start = 0ULL;
    uint64_t end = 0ULL;
    for (const MemRange& r : ranges) {
        if (start == 0ULL || r.start < start) start = r.start;
        if (r.end > end) end = r.end;
    }
    if (start == 0ULL || end <= start) return false;
    *out_start = start;
    *out_end = end;
    return true;
}

inline bool matches_at(const unsigned char* buf, size_t i,
                       const unsigned char* pattern, const unsigned char* mask, size_t len) {
    for (size_t k = 0; k < len; ++k) {
        if (mask[k] == 0) continue;
        if (buf[i + k] != pattern[k]) return false;
    }
    return true;
}

}

extern "C" {

JNIEXPORT jint JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativePid(JNIEnv*, jclass) {
    return static_cast<jint>(getpid());
}

JNIEXPORT jboolean JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeReadable(JNIEnv*, jclass, jlong address) {
    if (address <= 0) return JNI_FALSE;
    unsigned char probe = 0;
    return read_at(static_cast<uint64_t>(address), &probe, 1) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeModuleBase(JNIEnv* env, jclass, jstring name) {
    if (name == nullptr) return 0;

    const char* raw = env->GetStringUTFChars(name, nullptr);
    if (raw == nullptr) return 0;
    std::string needle(raw);
    env->ReleaseStringUTFChars(name, raw);
    if (needle.empty()) return 0;

    uint64_t start = 0ULL;
    uint64_t end = 0ULL;
    if (!module_bounds(needle, &start, &end)) return 0;
    return static_cast<jlong>(start);
}

JNIEXPORT jlong JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeModuleSize(JNIEnv* env, jclass, jstring name) {
    if (name == nullptr) return 0;

    const char* raw = env->GetStringUTFChars(name, nullptr);
    if (raw == nullptr) return 0;
    std::string needle(raw);
    env->ReleaseStringUTFChars(name, raw);
    if (needle.empty()) return 0;

    uint64_t start = 0ULL;
    uint64_t end = 0ULL;
    if (!module_bounds(needle, &start, &end)) return 0;
    return static_cast<jlong>(end - start);
}

JNIEXPORT jfloat JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeReadFloat(JNIEnv*, jclass, jlong address) {
    if (address <= 0) return static_cast<jfloat>(NAN);
    float value = 0.0f;
    if (!read_at(static_cast<uint64_t>(address), &value, sizeof(value))) {
        return static_cast<jfloat>(NAN);
    }
    return static_cast<jfloat>(value);
}

JNIEXPORT jint JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeReadInt(JNIEnv*, jclass, jlong address) {
    if (address <= 0) return 0;
    int32_t value = 0;
    if (!read_at(static_cast<uint64_t>(address), &value, sizeof(value))) return 0;
    return static_cast<jint>(value);
}

JNIEXPORT jlong JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeReadLong(JNIEnv*, jclass, jlong address) {
    if (address <= 0) return 0;
    int64_t value = 0;
    if (!read_at(static_cast<uint64_t>(address), &value, sizeof(value))) return 0;
    return static_cast<jlong>(value);
}

JNIEXPORT jbyteArray JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeReadCStringBytes(
        JNIEnv* env, jclass, jlong address, jint max_len) {
    if (address <= 0) return nullptr;
    if (max_len <= 0 || max_len > 4096) max_len = 256;

    const uint64_t base = static_cast<uint64_t>(address);
    std::vector<unsigned char> buffer(static_cast<size_t>(max_len), 0);
    size_t valid_len = 0;

    if (read_at(base, buffer.data(), buffer.size())) {
        valid_len = buffer.size();
    } else {
        for (size_t i = 0; i < buffer.size(); ++i) {
            unsigned char b = 0;
            if (!read_at(base + i, &b, 1)) {
                if (i == 0) return nullptr;
                break;
            }
            buffer[i] = b;
            valid_len = i + 1;
            if (b == 0) break;
        }
    }

    size_t str_len = 0;
    while (str_len < valid_len && buffer[str_len] != 0) {
        ++str_len;
    }

    jbyteArray out = env->NewByteArray(static_cast<jsize>(str_len));
    if (out == nullptr) return nullptr;
    if (str_len > 0) {
        env->SetByteArrayRegion(
            out,
            0,
            static_cast<jsize>(str_len),
            reinterpret_cast<const jbyte*>(buffer.data())
        );
    }
    return out;
}

JNIEXPORT jfloatArray JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeReadFloatArray(
        JNIEnv* env, jclass, jlong address, jint count) {
    if (address <= 0) return nullptr;
    if (count <= 0) count = 1;
    if (count > 256) count = 256;

    jfloatArray out = env->NewFloatArray(static_cast<jsize>(count));
    if (out == nullptr) return nullptr;

    const uint64_t base = static_cast<uint64_t>(address);
    std::vector<float> values(static_cast<size_t>(count), NAN);
    if (!read_at(base, values.data(), values.size() * sizeof(float))) {
        std::fill(values.begin(), values.end(), NAN);
        for (jint i = 0; i < count; ++i) {
            float v = 0.0f;
            if (read_at(base + static_cast<uint64_t>(i) * sizeof(float), &v, sizeof(v))) {
                values[static_cast<size_t>(i)] = v;
            }
        }
    }
    env->SetFloatArrayRegion(out, 0, static_cast<jsize>(count), values.data());
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeReadByteArray(
        JNIEnv* env, jclass, jlong address, jint count) {
    if (address <= 0) return nullptr;
    if (count <= 0) count = 1;
    if (count > 4096) count = 4096;

    const uint64_t base = static_cast<uint64_t>(address);
    std::vector<unsigned char> values(static_cast<size_t>(count), 0);
    if (!read_at(base, values.data(), values.size())) {
        std::fill(values.begin(), values.end(), 0);
        for (jint i = 0; i < count; ++i) {
            unsigned char b = 0;
            if (read_at(base + static_cast<uint64_t>(i), &b, 1)) {
                values[static_cast<size_t>(i)] = b;
            }
        }
    }

    jbyteArray out = env->NewByteArray(static_cast<jsize>(count));
    if (out == nullptr) return nullptr;
    env->SetByteArrayRegion(out, 0, static_cast<jsize>(count), reinterpret_cast<const jbyte*>(values.data()));
    return out;
}

JNIEXPORT jlongArray JNICALL
Java_dev_empyreal_core_jni_MemoryBridge_nativeScan(
        JNIEnv* env, jclass, jstring module,
        jbyteArray pattern, jbyteArray mask, jint max_results) {

    if (module == nullptr || pattern == nullptr || mask == nullptr) return nullptr;

    const char* raw = env->GetStringUTFChars(module, nullptr);
    if (raw == nullptr) return nullptr;
    std::string needle(raw);
    env->ReleaseStringUTFChars(module, raw);
    if (needle.empty()) return env->NewLongArray(0);

    const jsize pat_len = env->GetArrayLength(pattern);
    const jsize mask_len = env->GetArrayLength(mask);
    if (pat_len <= 0 || pat_len != mask_len || static_cast<size_t>(pat_len) > kScanChunk) {
        return nullptr;
    }

    std::vector<unsigned char> pat(static_cast<size_t>(pat_len));
    std::vector<unsigned char> msk(static_cast<size_t>(pat_len));
    env->GetByteArrayRegion(pattern, 0, pat_len, reinterpret_cast<jbyte*>(pat.data()));
    env->GetByteArrayRegion(mask, 0, mask_len, reinterpret_cast<jbyte*>(msk.data()));

    if (max_results <= 0) max_results = 64;
    if (static_cast<size_t>(max_results) > kMaxScanResults) {
        max_results = static_cast<jint>(kMaxScanResults);
    }

    const std::vector<MemRange> ranges = module_ranges(needle);
    if (ranges.empty()) {
        return env->NewLongArray(0);
    }

    std::vector<jlong> hits;
    hits.reserve(static_cast<size_t>(max_results));

    const size_t overlap = static_cast<size_t>(pat_len) - 1;
    std::vector<unsigned char> buf(kScanChunk);

    for (const MemRange& range : ranges) {
        if (!range.readable || range.end <= range.start) continue;
        if (hits.size() >= static_cast<size_t>(max_results)) break;

        uint64_t pos = range.start;

        while (pos < range.end && hits.size() < static_cast<size_t>(max_results)) {
            const uint64_t remaining = range.end - pos;
            const size_t want = static_cast<size_t>(
                std::min<uint64_t>(kScanChunk, remaining)
            );
            if (want < static_cast<size_t>(pat_len)) break;
            if (!read_at(pos, buf.data(), want)) {
                constexpr uint64_t kPageStep = 4096ULL;
                const uint64_t next_page = (pos + kPageStep) & ~(kPageStep - 1ULL);
                if (next_page <= pos || next_page >= range.end) break;
                pos = next_page;
                continue;
            }

            const size_t limit = want - static_cast<size_t>(pat_len);

            for (size_t i = 0; i <= limit && hits.size() < static_cast<size_t>(max_results); ++i) {
                if (matches_at(buf.data(), i, pat.data(), msk.data(), static_cast<size_t>(pat_len))) {
                    hits.push_back(static_cast<jlong>(pos + static_cast<uint64_t>(i)));
                }
            }

            if (want < kScanChunk) break;
            pos += static_cast<uint64_t>(kScanChunk - overlap);
        }
    }

    jlongArray out = env->NewLongArray(static_cast<jsize>(hits.size()));
    if (out == nullptr) return nullptr;
    if (!hits.empty()) {
        env->SetLongArrayRegion(out, 0, static_cast<jsize>(hits.size()), hits.data());
    }
    return out;
}

}
