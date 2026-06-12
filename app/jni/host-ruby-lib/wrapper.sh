#!/bin/sh
RUBYLIB="$(dirname "$0")" exec /usr/bin/ruby --disable=gems "$@"
