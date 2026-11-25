#pragma once

#include <string>
#include <vector>
#include <sys/types.h>

namespace termlinux::process {

struct WaitResult {
    bool exited = false;
    bool signaled = false;
    int exit_code = -1;
    int signal = 0;
    int err = 0;
};

pid_t spawn_pty_process(
    int master_fd,
    const std::string& slave_path,
    const std::string& executable,
    const std::vector<std::string>& argv,
    const std::vector<std::string>& envp,
    const std::string& cwd,
    int* out_err
);

WaitResult wait_pid(pid_t pid, bool no_hang);
int kill_process(pid_t pid, int sig);
bool is_process_alive(pid_t pid);

} // namespace termlinux::process
