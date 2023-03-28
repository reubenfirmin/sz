package main

import (
	"fmt"
	"sort"
)

func report(dir string, results map[string]int64) {
	type kv struct {
		key string
		value int64
	}

	var entries []kv
	var totalSize int64 = 0
	for path, size := range results {
		totalSize += size
		entries = append(entries, kv{key: path, value: size})
	}

	var onePercent = totalSize / 100.0

	sort.Slice(entries, func(i, j int) bool {
		return entries[i].value > entries[j].value
	})

	fmt.Println(dir, " files size ", results[dir])
	fmt.Println(dir, " total size ", totalSize)
	println("------------------------------------------------")

	for _, entry := range entries {
		if entry.value > onePercent {
			fmt.Println(entry.value, "\t\t", entry.key)
		}
	}
}