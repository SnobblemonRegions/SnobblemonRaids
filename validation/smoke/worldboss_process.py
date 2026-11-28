"""Stable Windows process handles for disposable rehearsal crash tests only."""
import ctypes
from ctypes import wintypes
import os


class WindowsProcess:
    def __init__(self, pid, *, terminate=False):
        if os.name != "nt" or type(pid) is not int or pid <= 0:
            raise ValueError("Positive Windows process ID required")
        self.api = ctypes.WinDLL("kernel32", use_last_error=True)
        self.api.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        self.api.OpenProcess.restype = wintypes.HANDLE
        self.api.CloseHandle.argtypes = [wintypes.HANDLE]
        self.api.GetProcessTimes.argtypes = [wintypes.HANDLE, *([ctypes.POINTER(wintypes.FILETIME)] * 4)]
        self.api.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
        self.api.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
        self.api.WaitForSingleObject.restype = wintypes.DWORD
        self.api.TerminateProcess.argtypes = [wintypes.HANDLE, wintypes.UINT]
        self.handle = self.api.OpenProcess(0x1000 | 0x100000 | (1 if terminate else 0), False, pid)
        if not self.handle:
            raise ctypes.WinError(ctypes.get_last_error())

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.api.CloseHandle(self.handle)

    def identity(self):
        times = [wintypes.FILETIME() for _ in range(4)]
        if not self.api.GetProcessTimes(self.handle, *(ctypes.byref(t) for t in times)):
            raise ctypes.WinError(ctypes.get_last_error())
        size = wintypes.DWORD(32768)
        name = ctypes.create_unicode_buffer(size.value)
        if not self.api.QueryFullProcessImageNameW(self.handle, 0, name, ctypes.byref(size)):
            raise ctypes.WinError(ctypes.get_last_error())
        return {"creation_filetime": (times[0].dwHighDateTime << 32) | times[0].dwLowDateTime,
                "exe": name.value}

    def alive(self):
        result = self.api.WaitForSingleObject(self.handle, 0)
        if result not in (0, 258):
            raise ctypes.WinError(ctypes.get_last_error())
        return result == 258

    def crash(self):
        if not self.alive():
            raise ValueError("Owned process already exited")
        if not self.api.TerminateProcess(self.handle, 0xC0B0):
            raise ctypes.WinError(ctypes.get_last_error())
        if self.api.WaitForSingleObject(self.handle, 30000) != 0:
            raise RuntimeError("Owned process did not exit within 30 seconds")


def validate_identity(manifest, identity):
    birth = manifest.get("process_creation_filetime")
    if type(birth) is not int or birth <= 0:
        raise ValueError("Recorded process creation identity required; use a fresh rehearsal")
    if (identity["creation_filetime"] != birth
            or os.path.normcase(identity["exe"]) != os.path.normcase(manifest["java"])):
        raise ValueError("Recorded PID was reused or belongs to a different executable")


def previous_exited(manifest):
    if type(manifest.get("process_creation_filetime")) is not int:
        raise ValueError("Restart requires recorded process creation identity")
    try:
        with WindowsProcess(manifest["pid"]) as process:
            if not process.alive():
                return True
            try:
                identity = process.identity()
            except OSError:
                if not process.alive():  # Process can exit between the liveness check and image query.
                    return True
                raise
            if identity["creation_filetime"] != manifest["process_creation_filetime"]:
                return True  # Never signal the new owner of a reused PID.
            validate_identity(manifest, identity)
            return not process.alive()
    except OSError as missing:
        if getattr(missing, "winerror", None) == 87:  # ERROR_INVALID_PARAMETER: no process with this PID.
            return True
        raise
