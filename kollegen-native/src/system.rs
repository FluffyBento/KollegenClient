use std::sync::atomic::{AtomicU64, Ordering};

static CACHED_RSS: AtomicU64 = AtomicU64::new(0);
static LAST_CHECK_MS: AtomicU64 = AtomicU64::new(0);

pub fn resident_memory_bytes() -> u64 {
    #[cfg(any(target_os = "linux", target_os = "android"))]
    {
        let now_ms = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0);
        let last = LAST_CHECK_MS.load(Ordering::Relaxed);
        if now_ms.saturating_sub(last) < 1000 {
            let cached = CACHED_RSS.load(Ordering::Relaxed);
            if cached > 0 {
                return cached;
            }
        }
        if let Ok(statm) = std::fs::read_to_string("/proc/self/statm") {
            if let Some(second) = statm.split_whitespace().nth(1) {
                if let Ok(pages) = second.parse::<u64>() {
                    let page_size = unsafe { libc::sysconf(libc::_SC_PAGESIZE) };
                    if page_size > 0 {
                        let res = pages.saturating_mul(page_size as u64);
                        CACHED_RSS.store(res, Ordering::Relaxed);
                        LAST_CHECK_MS.store(now_ms, Ordering::Relaxed);
                        return res;
                    }
                }
            }
        }
        CACHED_RSS.load(Ordering::Relaxed)
    }
    #[cfg(windows)]
    {
        use windows_sys::Win32::System::ProcessStatus::{K32GetProcessMemoryInfo, PROCESS_MEMORY_COUNTERS};
        use windows_sys::Win32::System::Threading::GetCurrentProcess;
        unsafe {
            let mut pmc: PROCESS_MEMORY_COUNTERS = std::mem::zeroed();
            pmc.cb = std::mem::size_of::<PROCESS_MEMORY_COUNTERS>() as u32;
            if K32GetProcessMemoryInfo(GetCurrentProcess(), &mut pmc, pmc.cb) != 0 {
                pmc.WorkingSetSize as u64
            } else {
                0
            }
        }
    }
    #[cfg(not(any(target_os = "linux", target_os = "android", windows)))]
    {
        0
    }
}

pub fn feature_string() -> String {
    let mut features = Vec::new();
    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") {
            features.push("AVX2");
        }
        if is_x86_feature_detected!("fma") {
            features.push("FMA");
        }
        if is_x86_feature_detected!("sse4.2") {
            features.push("SSE4.2");
        }
        if is_x86_feature_detected!("bmi2") {
            features.push("BMI2");
        }
    }
    #[cfg(target_arch = "aarch64")]
    {
        features.push("NEON");
    }
    #[cfg(any(target_os = "linux", target_os = "android"))]
    {
        features.push("POSIX-SCHED");
    }
    format!("Kollegen Native Rust Engine v1.26.1 [{}]", features.join("+"))
}
