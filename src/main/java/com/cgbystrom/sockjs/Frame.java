package com.cgbystrom.sockjs;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.util.CharsetUtil;

public abstract class Frame {
    private static final OpenFrame OPEN_FRAME_OBJ = new OpenFrame();
    private static final ChannelBuffer OPEN_FRAME = ChannelBuffers.copiedBuffer("o", CharsetUtil.UTF_8);
    private static final ChannelBuffer OPEN_FRAME_NL = ChannelBuffers.copiedBuffer("o\n", CharsetUtil.UTF_8);
    private static final HeartbeatFrame HEARTBEAT_FRAME_OBJ = new HeartbeatFrame();
    private static final ChannelBuffer HEARTBEAT_FRAME = ChannelBuffers.copiedBuffer("h", CharsetUtil.UTF_8);
    private static final ChannelBuffer HEARTBEAT_FRAME_NL = ChannelBuffers.copiedBuffer("h\n", CharsetUtil.UTF_8);
    private static final PreludeFrame PRELUDE_FRAME_OBJ = new PreludeFrame();
    private static final ChannelBuffer PRELUDE_FRAME = generatePreludeFrame('h', 2048, false);
    private static final ChannelBuffer PRELUDE_FRAME_NL = generatePreludeFrame('h', 2048, true);
    private static final ChannelBuffer NEW_LINE = ChannelBuffers.copiedBuffer("\n", CharsetUtil.UTF_8);

    protected ChannelBuffer data;
    
    public ChannelBuffer getData() {
        return data;
    }

    public static OpenFrame openFrame() {
        return OPEN_FRAME_OBJ;
    }

    public static CloseFrame closeFrame(int status, String reason) {
        return new CloseFrame(status, reason);
    }

    public static HeartbeatFrame heartbeatFrame() {
        return HEARTBEAT_FRAME_OBJ;
    }

    /** Used by XHR streaming */
    public static PreludeFrame preludeFrame() {
        return PRELUDE_FRAME_OBJ;
    }

    public static MessageFrame messageFrame(SockJsMessage... messages) {
        return new MessageFrame(messages);
    }
    
    public static ChannelBuffer encode(Frame frame, boolean appendNewline) {
        if (frame instanceof OpenFrame) {
            return appendNewline ? OPEN_FRAME_NL : OPEN_FRAME;
        } else if (frame instanceof HeartbeatFrame) {
            return appendNewline ? HEARTBEAT_FRAME_NL : HEARTBEAT_FRAME;
        } else if (frame instanceof PreludeFrame) {
            return appendNewline ? PRELUDE_FRAME_NL : PRELUDE_FRAME;
        } else if (frame instanceof MessageFrame || frame instanceof CloseFrame) {
            return appendNewline ? ChannelBuffers.wrappedBuffer(frame.getData(), NEW_LINE) : frame.getData();
        } else {
            throw new IllegalArgumentException("Unknown frame type passed: " + frame.getClass().getSimpleName());
        }
    }

    private static ChannelBuffer generatePreludeFrame(char c, int num, boolean appendNewline) {
        ChannelBuffer cb = ChannelBuffers.buffer(num + 1);
        for (int i = 0; i < num; i++) {
              cb.writeByte(c);
        }
        if (appendNewline)
            cb.writeByte('\n');
        return cb;
    }

    public static void escapeJson(String value, ChannelBuffer buffer) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch(ch) {
                case '"': buffer.writeByte('\\'); buffer.writeByte('\"'); break;
                case '/': buffer.writeByte('\\'); buffer.writeByte('/'); break;
                case '\\': buffer.writeByte('\\'); buffer.writeByte('\\'); break;
                case '\b': buffer.writeByte('\\'); buffer.writeByte('b'); break;
                case '\f': buffer.writeByte('\\'); buffer.writeByte('f'); break;
                case '\n': buffer.writeByte('\\'); buffer.writeByte('n'); break;
                case '\r': buffer.writeByte('\\'); buffer.writeByte('r'); break;
                case '\t': buffer.writeByte('\\'); buffer.writeByte('t'); break;

                default:
                    // Reference: http://www.unicode.org/versions/Unicode5.1.0/
                    if ((ch >= '\u0000' && ch <= '\u001F') ||
                            (ch >= '\u007F' && ch <= '\u009F') ||
                            (ch >= '\u2000' && ch <= '\u20FF')) {
                        String ss = Integer.toHexString(ch);
                        buffer.writeByte('\\');
                        buffer.writeByte('u');
                        for (int k = 0; k < 4 - ss.length(); k++) {
                            buffer.writeByte('0');
                        }
                        buffer.writeBytes(ss.toLowerCase().getBytes());
                    } else {
                        buffer.writeByte(ch);
                    }
            }
        }
    }

    public static void escapeJson(ChannelBuffer input, ChannelBuffer buffer) {
        for (int i = 0; i < input.readableBytes(); i++) {
            byte ch = input.getByte(i);
            switch(ch) {
                case '"': buffer.writeByte('\\'); buffer.writeByte('\"'); break;
                case '/': buffer.writeByte('\\'); buffer.writeByte('/'); break;
                case '\\': buffer.writeByte('\\'); buffer.writeByte('\\'); break;
                case '\b': buffer.writeByte('\\'); buffer.writeByte('b'); break;
                case '\f': buffer.writeByte('\\'); buffer.writeByte('f'); break;
                case '\n': buffer.writeByte('\\'); buffer.writeByte('n'); break;
                case '\r': buffer.writeByte('\\'); buffer.writeByte('r'); break;
                case '\t': buffer.writeByte('\\'); buffer.writeByte('t'); break;

                default:
                    // Reference: http://www.unicode.org/versions/Unicode5.1.0/
                    if ((ch >= '\u0000' && ch <= '\u001F') ||
                            (ch >= '\u007F' && ch <= '\u009F') ||
                            (ch >= '\u2000' && ch <= '\u20FF')) {
                        String ss = Integer.toHexString(ch);
                        buffer.writeByte('\\');
                        buffer.writeByte('u');
                        for (int k = 0; k < 4 - ss.length(); k++) {
                            buffer.writeByte('0');
                        }
                        buffer.writeBytes(ss.toLowerCase().getBytes());
                    } else {
                        buffer.writeByte(ch);
                    }
            }
        }
    }

    private static void escapeEventSource(ChannelBuffer input, ChannelBuffer output) {
        for (int i = 0; i < input.readableBytes(); i++) {
            byte b = input.getByte(i);
            String e = Integer.toHexString(b).toUpperCase();
            if (e.length() == 1) e = "0" + e;
            switch (b) {
                case '%': output.writeByte('%'); output.writeByte(e.charAt(0)); output.writeByte(e.charAt(1)); break;
                case '\r': output.writeByte('%'); output.writeByte(e.charAt(0)); output.writeByte(e.charAt(1)); break;
                case '\n': output.writeByte('%'); output.writeByte(e.charAt(0)); output.writeByte(e.charAt(1)); break;
                case '\0': output.writeByte('%'); output.writeByte(e.charAt(0)); output.writeByte(e.charAt(1)); break;
                default:
                    output.writeByte(b);
            }
        }
    }

    public static class OpenFrame extends Frame {
        @Override
        public ChannelBuffer getData() {
            return OPEN_FRAME;
        }
    }

    public static class CloseFrame extends Frame {
        private int status;
        private String reason;

        private CloseFrame(int status, String reason) {
            this.status = status;
            this.reason = reason;
            // FIXME: Must escape status and reason
            data = ChannelBuffers.copiedBuffer("c[" + status + ",\"" + reason + "\"]", CharsetUtil.UTF_8);
        }

        public int getStatus() {
            return status;
        }

        public String getReason() {
            return reason;
        }
    }

    public static class MessageFrame extends Frame {
        private MessageFrame(SockJsMessage... messages) {
            data = ChannelBuffers.dynamicBuffer();
            data.writeByte('a');
            data.writeByte('[');
            for (int i = 0; i < messages.length; i++) {
                SockJsMessage message = messages[i];
                data.writeByte('"');
                // FIXME: Really UTF-8 safe?
                escapeJson(message.getMessage(), data);
                data.writeByte('"');
                if (i < messages.length - 1) {
                    data.writeByte(',');
                }
            }

            data.writeByte(']');
        }
    }

    public static class HeartbeatFrame extends Frame {
        @Override
        public ChannelBuffer getData() {
            return HEARTBEAT_FRAME;
        }
    }

    public static class PreludeFrame extends Frame {
        @Override
        public ChannelBuffer getData() {
            return PRELUDE_FRAME;
        }
    }
}
