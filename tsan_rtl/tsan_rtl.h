/* Copyright (c) 2010-2011, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// Author: glider@google.com (Alexander Potapenko)

#ifndef TSAN_RTL_H_
#define TSAN_RTL_H_

#include <errno.h>
#include <signal.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>

#include "thread_sanitizer.h"
#include "suppressions.h"
#include "ts_util.h"

#ifdef __x86_64__
# define TSAN_RTL_X64
#else
# define TSAN_RTL_X86
#endif

#ifndef DEBUG
#define DEBUG 0
#endif

#if (DEBUG)
#define CHECK_IN_RTL() do { \
  assert((IN_RTL >= 0) && (IN_RTL <= 5)); \
} while (0)
#else
#define CHECK_IN_RTL()
#endif

#define ENTER_RTL() do { \
  IN_RTL++; \
  CHECK_IN_RTL(); \
} while (0)

#define LEAVE_RTL() do { \
  IN_RTL--; \
  CHECK_IN_RTL(); \
} while (0)

#ifdef DEBUG_LEVEL
#define DEBUG 1
#endif

#if (DEBUG_LEVEL == 2)
# define DDPrintf(params...) \
    Printf(params)
#else
# define DDPrintf(params...)
#endif

#if (DEBUG)
# define DPrintf(params...) \
    Printf(params)
# define DEBUG_DO(code) \
  do { code } while (0)
#else
# define DPrintf(params...)
# define DEBUG_DO(code)
#endif

class GIL {
 public:
  GIL() {
    Lock();
  }
  ~GIL() {
    Unlock();
  }

  static void Lock();

  static bool TryLock();
  static void Unlock();
  static bool UnlockNoSignals();
#if (DEBUG)
  static int GetDepth();
#endif
};

typedef uintptr_t pc_t;
typedef uintptr_t tid_t;
typedef map<pc_t, string> PcToStringMap;

struct ThreadInfo {
  TSanThread *thread;
  tid_t tid;
  int *thread_local_ignore;
};

tid_t GetTid();
// Reentrancy counter
extern __thread int IN_RTL;
extern __thread CallStackPod __tsan_shadow_stack;
extern __thread ThreadInfo INFO;

void ReadElf();
void AddWrappersDbgInfo();
void ReadDbgInfo(string filename);
string GetSelfFilename();

#define DECLARE_TID() \
  tid_t const tid = GetTid();

#define DECLARE_TID_AND_PC() \
  tid_t const tid = GetTid(); \
  pc_t const pc = (pc_t)__builtin_return_address(0);

#define EX_DECLARE_TID_AND_PC() \
  tid_t const tid = ExGetTid(); \
  pc_t const pc = (pc_t)__builtin_return_address(0);

void MaybeInitTid();

// Interface exposed to the instrumented program
extern "C" {
void flush_tleb();
void flush_dtleb_segv();
void flush_dtleb_nosegv();
void bb_flush_current(TraceInfoPOD *curr_mops);
void bb_flush_mop(TraceInfoPOD *curr_mop, uintptr_t addr);
void shadow_stack_check(uintptr_t old_v, uintptr_t new_v);
void *rtl_memcpy(char *dest, const char *src, size_t n);
void *rtl_memmove(char *dest, const char *src, size_t n);
}

inline void Put(EventType type, tid_t tid, pc_t pc,
                uintptr_t a, uintptr_t info);
inline void SPut(EventType type, tid_t tid, pc_t pc,
                 uintptr_t a, uintptr_t info);
inline void RPut(EventType type, tid_t tid, pc_t pc,
                 uintptr_t a, uintptr_t info);

// Put a synchronization event to ThreadSanitizer.
extern void ExPut(EventType type, tid_t tid, pc_t pc,
                        uintptr_t a, uintptr_t info);
extern void ExSPut(EventType type, tid_t tid, pc_t pc,
                        uintptr_t a, uintptr_t info);
extern void ExRPut(EventType type, tid_t tid, pc_t pc,
                        uintptr_t a, uintptr_t info);


void IGNORE_ALL_ACCESSES_AND_SYNC_BEGIN(void);
void IGNORE_ALL_ACCESSES_AND_SYNC_END(void);
extern tid_t ExGetTid();
void set_global_ignore(bool new_value);
void PrintStackTrace();

void *sys_mmap(void *addr, size_t length, int prot, int flags,
               int fd, off_t offset);
int sys_munmap(void *addr, size_t length);

#include "tsan_rtl_wrap.h"

#endif  // TSAN_RTL_H_
