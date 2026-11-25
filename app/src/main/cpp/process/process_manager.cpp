#include "process_manager.h"

#include <cerrno>
#include <csignal>
#include <dirent.h>
#include <fcntl.h>
#include <cstdlib>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <unistd.h>

namespace termlinux::process {

namespace {

void close_inherited_fds(int keep_fd) {
    DIR* dir = opendir("/proc/self/fd");
    if (dir != nullptr) {
        int dir_fd = dirfd(dir);
        struct dirent* entry;
        while ((entry = readdir(dir)) != nullptr) {
            char* endptr = nullptr;
            long fd = strtol(entry->d_name, &endptr, 10);
            if (endptr != entry->d_name && *endptr == '\0' && fd > 2 && fd != dir_fd && fd != keep_fd) {
                close(static_cast<int>(fd));
            }
        }
        closedir(dir);
    } else {
        for (int fd = 3; fd < 1024; ++fd) {
            if (fd != keep_fd) close(fd);
        }
    }
}

void reset_signals() {
    sigset_t empty;
    sigemptyset(&empty);
    sigprocmask(SIG_SETMASK, &empty, nullptr);

    struct sigaction sa {};
    sa.sa_handler = SIG_DFL;
    sigemptyset(&sa.sa_mask);
    for (int sig = 1; sig < NSIG; ++sig) {
        if (sig != SIGKILL && sig != SIGSTOP) {
            sigaction(sig, &sa, nullptr);
        }
    }
}

} // namespace

pid_t spawn_pty_process(
    int master_fd,
    const std::string& slave_path,
    const std::string& executable,
    const std::vector<std::string>& argv,
    const std::vector<std::string>& envp,
    const std::string& cwd,
    int* out_err
) {
    std::vector<char*> c_argv;
    c_argv.reserve(argv.size() + 1);
    for (const auto& arg : argv) {
        c_argv.push_back(const_cast<char*>(arg.c_str()));
    }
    c_argv.push_back(nullptr);

    std::vector<char*> c_envp;
    c_envp.reserve(envp.size() + 1);
    for (const auto& env : envp) {
        c_envp.push_back(const_cast<char*>(env.c_str()));
    }
    c_envp.push_back(nullptr);

    pid_t pid = fork();
    if (pid < 0) {
        if (out_err) *out_err = errno;
        return -1;
    }

    if (pid == 0) {
        close(master_fd);
        reset_signals();

        if (setsid() < 0) {
            _exit(126);
        }

        int slave_fd = open(slave_path.c_str(), O_RDWR);
        if (slave_fd < 0) {
            _exit(126);
        }

        if (ioctl(slave_fd, TIOCSCTTY, 0) < 0) {
            close(slave_fd);
            _exit(126);
        }

        dup2(slave_fd, STDIN_FILENO);
        dup2(slave_fd, STDOUT_FILENO);
        dup2(slave_fd, STDERR_FILENO);
        if (slave_fd > 2) {
            close(slave_fd);
        }

        close_inherited_fds(-1);

        if (!cwd.empty()) {
            if (chdir(cwd.c_str()) != 0) {
                chdir("/");
            }
        }

        execve(executable.c_str(), c_argv.data(), c_envp.data());
        _exit(127);
    }

    return pid;
}

WaitResult wait_pid(pid_t pid, bool no_hang) {
    WaitResult result;
    int status = 0;
    int options = no_hang ? WNOHANG : 0;
    pid_t ret = waitpid(pid, &status, options);
    if (ret < 0) {
        result.err = errno;
        return result;
    }
    if (ret == 0) {
        return result;
    }
    if (WIFEXITED(status)) {
        result.exited = true;
        result.exit_code = WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        result.signaled = true;
        result.signal = WTERMSIG(status);
    }
    return result;
}

int kill_process(pid_t pid, int sig) {
    if (pid <= 0) return -EINVAL;
    if (kill(-pid, sig) != 0 && errno != ESRCH) {
        // Fallback to direct pid if process group doesn't exist
        if (kill(pid, sig) != 0) {
            return -errno;
        }
    }
    return 0;
}

bool is_process_alive(pid_t pid) {
    if (pid <= 0) return false;
    return kill(pid, 0) == 0 || errno == EPERM;
}

} // namespace termlinux::process
