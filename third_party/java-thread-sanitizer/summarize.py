#!/usr/bin/python2.4

import os
import re
import sys

tmpdir = sys.argv[1]
print tmpdir

# Parse java output.
java_log = open(os.path.join(tmpdir, "java.log"), "r")
test_re = re.compile("===== ([^ ]+) ====")
exc_re = re.compile("Exception occured during transformation")
disable_re = re.compile("EXCL (.*)")
results = {}
tests = []
results_tsan = {}
disable_str = ""
disabled = 0
test = ""
for line in java_log:
  match_exc = False
  m = test_re.search(line)
  if m:
    test = m.group(1)
    tests.append(test)
    results[test] = True
    results_tsan[test] = False
  else:
    match_exc = exc_re.search(line)
  if test != "" and match_exc:
    results[test] = False
  if disable_re.search(line):
    disable_str += line
    disabled += 1
java_log.close()

# Parse offline ThreadSanitizer output.
tsan_log = open(os.path.join(tmpdir, "tsan.log"), "r")
warning_re = re.compile("WARNING:")
test = ""
for line in tsan_log:
  m = test_re.search(line)
  if m:
    test = m.group(1)
    results_tsan[test] = True
  if warning_re.search(line):
    results[test] = False
tsan_log.close()

# Produce test report.
passed = 0
failed = 0
for test in tests:
  if results_tsan[test]:
    if results[test]:
      res = "PASS"
      passed += 1
    else:
      res = "FAIL"
      failed += 1
    print "%s %s" % (res, test)
  else:
    print "TSAN didn't handle test %s" % (test)
    sys.exit(1)
print disable_str
print "----"
print "passed: %d, failed: %d, excluded: %d, total: %d" % \
(passed, failed, disabled, passed + failed + disabled)
sys.exit(failed)
