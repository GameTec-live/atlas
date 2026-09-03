#!/bin/sh
set -eu

configuration_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)
mkdir -p build/etc/init.d
install -m 0755 "$configuration_dir/rcS" build/etc/init.d/rcS

# kpartx forces apt to install udev and systemd, although recovery only needs
# libudev/libsystemd for rpi-fastbootd's ABI. Keep those shared libraries and
# discard the daemons, service-manager payload and hardware databases.
rm -rf \
    build/etc/systemd \
    build/usr/lib/aarch64-linux-gnu/systemd \
    build/usr/lib/systemd \
    build/usr/lib/udev \
    build/var/lib/systemd \
    build/var/log/journal
rm -f \
    build/usr/bin/deb-systemd-helper \
    build/usr/bin/deb-systemd-invoke \
    build/usr/bin/busctl \
    build/usr/bin/hostnamectl \
    build/usr/bin/journalctl \
    build/usr/bin/kernel-install \
    build/usr/bin/localectl \
    build/usr/bin/loginctl \
    build/usr/bin/networkctl \
    build/usr/bin/run0 \
    build/usr/bin/systemctl \
    build/usr/bin/systemd-* \
    build/usr/bin/timedatectl \
    build/usr/bin/varlinkctl \
    build/usr/bin/udevadm

# Recovery only handles ASCII/UTF-8 paths and has no use for build-time docs.
rm -rf \
    build/usr/lib/aarch64-linux-gnu/gconv \
    build/usr/share/bash-completion \
    build/usr/share/common-licenses \
    build/usr/share/dbus-1 \
    build/usr/share/doc \
    build/usr/share/gcc \
    build/usr/share/initramfs-tools \
    build/usr/share/locale \
    build/usr/share/man \
    build/usr/share/perl5 \
    build/usr/share/polkit-1 \
    build/usr/share/zsh
rm -f \
    build/usr/bin/pzstd \
    build/usr/bin/unzstd \
    build/usr/bin/zstdcat \
    build/usr/bin/zstdgrep \
    build/usr/bin/zstdless \
    build/usr/bin/zstdmt \
    build/usr/lib/modules-load.d/fastbootd.conf
