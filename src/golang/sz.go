package main

import (
    "fmt"
    "os"
    "syscall"
)

func main() {
    dir := "/home/rfirmin" // Change this to the directory you want to stat
    fileInfo, _ := os.Lstat(dir)
    stat := fileInfo.Sys().(*syscall.Stat_t)
    device := stat.Dev

    dirInfo := processDir(dir, device)
    fmt.Println("dir ", dirInfo.Path, " size is ", dirInfo.Size)
    for _, subpath := range dirInfo.SubPaths {
        fmt.Println(subpath)
    }
}

func processDir(dir string, device uint64) DirResult {
    files, err := os.ReadDir(dir)
    result := DirResult{Path: dir}

    if err != nil {
        fmt.Println(err)
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
        if fileInfo.Mode() & os.ModeSymlink != 0 {
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
    Path string
    Size int64
    SubPaths []string
}