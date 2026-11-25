#pragma once

#include <string>

namespace termlinux::pty {

struct PtyPair {
    int master_fd = -1;
    std::string slave_path;
    int err = 0;
};

PtyPair open_pty(int cols, int rows);
int resize_pty(int master_fd, int cols, int rows);
void close_pty(int master_fd);

} // namespace termlinux::pty
