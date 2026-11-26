#[cfg(target_os = "linux")]
#[inline]
unsafe fn current_tid() -> libc::pid_t {
    libc::syscall(libc::SYS_gettid) as libc::pid_t
}

pub fn favour_current_thread() -> bool {
    #[cfg(target_os = "linux")]
    unsafe {
        let tid = current_tid();
        for prio in [-5, -2] {
            if libc::setpriority(libc::PRIO_PROCESS as _, tid as libc::id_t, prio) == 0 {
                return true;
            }
        }
        let mut param: libc::sched_param = std::mem::zeroed();
        param.sched_priority = 1;
        if libc::sched_setscheduler(tid, libc::SCHED_RR, &param) == 0 {
            return true;
        }
        libc::setpriority(libc::PRIO_PROCESS as _, tid as libc::id_t, 0) == 0
    }
    #[cfg(target_os = "android")]
    unsafe {
        for prio in [-5, -2, 0] {
            if libc::setpriority(libc::PRIO_PROCESS as _, 0, prio) == 0 {
                return true;
            }
        }
        false
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
    #[cfg(target_os = "linux")]
    unsafe {
        let tid = current_tid();
        libc::syscall(libc::SYS_ioprio_set, 1i64, tid as i64, (3i64 << 13) | 0i64);
        let param: libc::sched_param = std::mem::zeroed();
        let batch_ok = libc::sched_setscheduler(tid, libc::SCHED_BATCH, &param) == 0;
        let nice_ok = libc::setpriority(libc::PRIO_PROCESS as _, tid as libc::id_t, 8) == 0;
        if batch_ok || nice_ok {
            return true;
        }
        libc::sched_setscheduler(tid, libc::SCHED_IDLE, &param) == 0
    }
    #[cfg(target_os = "android")]
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
