# sz - find what's eating your disk space

## About

This is an improved version of `du`, written with Kotlin Native. It's intended to quickly answer the question that most of us
probably use `du` for, i.e. "what the hell is using all my disk space"?

It contains the following improvements:
* It's much faster
* It skips virtual filesystems like /proc and /sys
* It skips paths mounted on other devices from the starting path
* It sorts the output by file size, high to low
* It only returns results for directories using at least 1% of all files under the path
* It won't complain about directories it doesn't have permission to access; for example, running against / as a regular user will work, but the size will be less than if you run with sudo

## Running

./sz dir

* dir - directory to scan

Options:
* --threads - how many threads to execute when recursing the tree (best option is likely a function of number of cores)
* --pause - how many microseconds the main thread should pause when waiting for children to finish (best option is likely is a function of the underlying disk hardware)

Example:

./sz /tmp 50 100

It supplies sane defaults also:

./sz /home/you/Downloads 

## Status

Beta.

It works, output matches `du` (with differences noted above), and it is at least 2x as fast as `du` even without 
the skipping optimizations. With those optimizations it can be at least 10x as fast. 

It is not deeply battle tested or proven to be correct, and so is currently unsuitable for distribution. Extensive
tests need to be created.

Known issue: running twice with a high number of threads (e.g. 100) returns a nonsensical result the second time, which 
is a smell. 

## Building

apt install gcc-multilib
./build.sh
