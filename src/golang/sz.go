package main

import (
	"flag"
	"fmt"
	"os"
	"syscall"
)

var blacklist = map[string]struct{} {"/proc":{}, "/sys":{}}

/**
 * Like the rust version, this is my first go code, and is probably not idiomatic.
 */
func main() {

	// TODO finish adding all of the formatting options supported by the other versions
	var dir string

	// TODO understand precedence of default args
	flag.StringVar(&dir, "dir", "", "The directory")
	flag.StringVar(&dir, "d", "", "The directory")
	flag.Parse()

	// TODO surely a better way to provide positional / unflagged args
	if dir == "" {
		args := flag.Args()
		// TODO handle error/empty
		dir = args[0]
	}

	fileInfo, _ := os.Lstat(dir)
	stat := fileInfo.Sys().(*syscall.Stat_t)
	device := stat.Dev

	report(dir, scanPath(dir, device))
}

func scanPath(dir string, device uint64) map[string]int64 {

	ch := make(chan DirResult)

	results := make(map[string]int64)

	// TODO for now use goroutines rather than a pool of os threads. what does perf look like?
	pending := 1
	go submit(dir, device, ch)

	for pending > 0 {
		result := <-ch
		pending -= 1
		results[result.Path] = result.Size
		for _, subpath := range result.SubPaths {
			pending += 1
			go submit(subpath, device, ch)
		}
	}
	return results
}

func submit(dir string, device uint64, ch chan<- DirResult) {
	ch <- processDir(dir, device)
}

func processDir(dir string, device uint64) DirResult {
	files, err := os.ReadDir(dir)
	result := DirResult{Path: dir}

	if err != nil {
		//        fmt.Println(err)
		return result
	}

	for _, file := range files {
		path := dir + "/" + file.Name()
		fileInfo, err := os.Lstat(path)
		if err != nil {
			// silently ignore and continue!
			continue
		}
		stat := fileInfo.Sys().(*syscall.Stat_t)
		if fileInfo.Mode()&os.ModeSymlink != 0 {
			continue
		} else if _, blacklisted := blacklist[path]; blacklisted { // this "set" lookup is ugly
			fmt.Println("skipping ", path, blacklisted)
			continue
		} else if stat.Dev == device && fileInfo.IsDir() {
			result.SubPaths = append(result.SubPaths, path)
		} else {
			result.Size += fileInfo.Size()
		}
	}
	return result
}

type DirResult struct {
	Path     string
	Size     int64
	SubPaths []string
}
