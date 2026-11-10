pub fn favour_current_thread() -> bool {
    #[cfg(any(target_os = "linux", target_os = "android"))]
    unsafe {
        for prio in [-10, -5, -2, 0] {
            if libc::setpriority(libc::PRIO_PROCESS as _, 0, prio) == 0 {
                return true;
            }
        }
        let mut param: libc::sched_param = std::mem::zeroed();
        param.sched_priority = 1;
        libc::sched_setscheduler(0, libc::SCHED_RR, &param) == 0
    }
    #[cfg(windows)]
    unsafe {
        use windows_sys::Win32::System::Threading::{GetCurrentThread, SetThreadPriority, THREAD_PRIORITY_HIGHEST};
        SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_HIGHEST) != 0
    }
    #[cfg(not(any(target_os = "linux", target_os = "android", windows)))]
    {
        false
    }
}

pub fn idle_current_thread() -> bool {
    #[cfg(any(target_os = "linux", target_os = "android"))]
    unsafe {
        let param: libc::sched_param = std::mem::zeroed();
        libc::sched_setscheduler(0, libc::SCHED_IDLE, &param) == 0
    }
    #[cfg(windows)]
    unsafe {
        use windows_sys::Win32::System::Threading::{GetCurrentThread, SetThreadPriority, THREAD_PRIORITY_IDLE};
        SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_IDLE) != 0
    }
    #[cfg(not(any(target_os = "linux", target_os = "android", windows)))]
    {
        false
    }
}
