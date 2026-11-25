#include "pty_manager.h"

#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <termios.h>
#include <unistd.h>

namespace termlinux::pty {

PtyPair open_pty(int cols, int rows) {
    PtyPair result;
    int master_fd = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master_fd < 0) {
        result.err = errno;
        return result;
    }

    if (grantpt(master_fd) != 0 || unlockpt(master_fd) != 0) {
        result.err = errno;
        close(master_fd);
        return result;
    }

    char slave_buf[128] = {0};
    if (ptsname_r(master_fd, slave_buf, sizeof(slave_buf)) != 0) {
        result.err = errno;
        close(master_fd);
        return result;
    }

    struct termios tios {};
    if (tcgetattr(master_fd, &tios) == 0) {
        tios.c_iflag |= ICRNL | IXON;
#ifdef IUTF8
        tios.c_iflag |= IUTF8;
#endif
        tios.c_oflag |= OPOST | ONLCR;
        tios.c_lflag |= ISIG | ICANON | ECHO | ECHOE | ECHOK | IEXTEN;
        tios.c_cc[VERASE] = 0x7f;
        tcsetattr(master_fd, TCSANOW, &tios);
    }

    resize_pty(master_fd, cols, rows);

    result.master_fd = master_fd;
    result.slave_path = slave_buf;
    return result;
}

int resize_pty(int master_fd, int cols, int rows) {
    if (master_fd < 0) return -EINVAL;
    struct winsize ws {};
    ws.ws_col = static_cast<unsigned short>(cols > 0 ? cols : 80);
    ws.ws_row = static_cast<unsigned short>(rows > 0 ? rows : 24);
    ws.ws_xpixel = 0;
    ws.ws_ypixel = 0;
    if (ioctl(master_fd, TIOCSWINSZ, &ws) != 0) {
        return -errno;
    }
    return 0;
}

void close_pty(int master_fd) {
    if (master_fd >= 0) {
        close(master_fd);
    }
}

} // namespace termlinux::pty
