/* Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jtsan.writers;

import org.jtsan.EventType;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Write to {@code OutputStream} tsan events in binary format.
 *
 * @author Sergey Vorobyev
 */
public class BinaryEventWriter implements EventWriter {

  private DataOutputStream out;

  public void setOutputStream(OutputStream outputStream) {
    // TODO(vors): Implement final buffer flush - now we lose last events.
    out = new DataOutputStream(new BufferedOutputStream(outputStream));
  }

  @SuppressWarnings("fallthrough")
  public synchronized void writeEvent(EventType type, long tid, long pc, long address, long extra) {
    try {
      out.writeByte(type.ordinal());
      switch (type) {
        case THR_START:
          out.writeShort((int) extra);
        case READ :
        case READER_LOCK :
        case SIGNAL :
        case THR_JOIN_AFTER :
        case UNLOCK :
        case WAIT :
        case WRITE :
        case WRITER_LOCK :
          out.writeLong(address);
        case EXPECT_RACE_BEGIN :
        case EXPECT_RACE_END :
        case RTN_EXIT :
        case SBLOCK_ENTER :
        case STACK_TRACE :
        case THR_END :
        case THR_FIRST_INSN :
          out.writeInt((int) pc);
        case RTN_CALL :
          out.writeShort((int) tid);
        default:
          break;
      }
    }
    catch (IOException e) {
      throw new RuntimeException("Exception occurred when writing event: " +
          type + " " + tid + " " + pc + " " + address + " " + extra, e);
    }
  }

  public synchronized void writeCodePosition(long pc, String descr) {
    try {
      out.writeByte(EventType.PC_DESCRIPTION.ordinal());
      out.writeInt((int) pc);
      out.writeUTF(descr);
    }
    catch (IOException e) {
      throw new RuntimeException("Exception occurred during write code position " +
          pc + " with descr: " + descr, e);
    }
  }

  public synchronized void writeComment(String descr, long pc) {
    try {
      out.writeByte(EventType.PRINT_MESSAGE.ordinal());
      out.writeUTF(descr);
    }
    catch (IOException e) {
      throw new RuntimeException("Exception occurred during write comment: " +
          descr + ", pc=" + pc, e);
    }
  }
}
