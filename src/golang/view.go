package main

import (
	"fmt"
	"math"
	"sort"
	"strconv"
)

func report(dir string, results map[string]int64, options formatOptions) {
	type kv struct {
		key   string
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

	fmt.Println(dir, " files size ", format(results[dir], !options.Raw, !options.NoColors))
	fmt.Println(dir, " total size ", format(totalSize, !options.Raw, !options.NoColors))
	println("------------------------------------------------")

	for _, entry := range entries {
		if entry.value > onePercent {
			fmt.Println(format(entry.value, !options.Raw, !options.NoColors), "\t\t", entry.key)
		}
	}
}

func format(size int64, human bool, colors bool) string {
	if !human {
		return strconv.FormatInt(size, 10)
	} else {
		formatted := ""
		switch {
		case size > 1_000_000_000:
			formatted = round2(size, 1_000_000_000) + "G"
		case size > 1_000_000:
			formatted = round2(size, 1_000_000) + "M"
		case size > 1_000:
			formatted = round2(size, 1_000) + "K"
		default:
			formatted = strconv.FormatInt(size, 10)
		}
		return formatted
	}
}

func round2(size int64, divisor uint32) string {
	result := math.Round(float64(size)/float64(divisor)*100) / 100
	return strconv.FormatFloat(result, 'f', -1, 64)
}


