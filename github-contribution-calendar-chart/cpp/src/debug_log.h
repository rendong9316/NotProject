#pragma once
#include <stdio.h>
static inline void dbg_log(const char* fmt, ...) {
    va_list args;
    va_start(args, fmt);
    FILE* f = fopen("D:/Desktop/NotProjext/github-contribution-calendar-chart/cpp/build/debug.log", "a");
    if (f) {
        fprintf(f, "[%ld] ", (long)GetTickCount());
        vfprintf(f, fmt, args);
        fprintf(f, "\n");
        fclose(f);
    }
    va_end(args);
}
