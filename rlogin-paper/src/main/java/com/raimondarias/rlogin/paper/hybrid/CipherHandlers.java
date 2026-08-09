package com.raimondarias.rlogin.paper.hybrid;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;

import javax.crypto.Cipher;
import java.util.List;

/**
 * AES/CFB8 stream cipher handlers for a connection {@link HybridAuthListener}
 * has manually upgraded to encrypted mode. Once the client sends its
 * encryption response it switches to encrypted mode on its own end
 * immediately — these must already be installed in the pipeline by the time
 * that happens, or every subsequent byte from the client is undecryptable
 * garbage and the connection breaks.
 *
 * <p>Inserted at vanilla Minecraft's own well-known, version-stable
 * pipeline handler names: "decrypt" before "splitter" (so the frame
 * decoder sees plaintext), "encrypt" before "prepender" (the length
 * prefix itself is inside the encrypted stream too, matching the real
 * protocol) — the exact same names and positions the vanilla server's own
 * connection setup uses, so this doesn't fight with anything else already
 * in the pipeline (including PacketEvents' own encoder/decoder).</p>
 */
final class CipherHandlers {

    private CipherHandlers() {
    }

    static final class Decrypt extends MessageToMessageDecoder<ByteBuf> {
        private final Cipher cipher;

        Decrypt(Cipher cipher) {
            this.cipher = cipher;
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
            byte[] bytes = new byte[msg.readableBytes()];
            msg.readBytes(bytes);
            out.add(Unpooled.wrappedBuffer(cipher.update(bytes)));
        }
    }

    static final class Encrypt extends MessageToByteEncoder<ByteBuf> {
        private final Cipher cipher;

        Encrypt(Cipher cipher) {
            this.cipher = cipher;
        }

        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) {
            byte[] bytes = new byte[in.readableBytes()];
            in.readBytes(bytes);
            out.writeBytes(cipher.update(bytes));
        }
    }
}
