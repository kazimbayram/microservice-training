package com.kazimbayram.elearn.resource_service.support;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public final class Mp3Fixture {

	private Mp3Fixture() {
	}

	public static byte[] audio() {
		ByteArrayOutputStream tags = new ByteArrayOutputStream();
		tag(tags, "TIT2", "  Original title  ");
		tag(tags, "TPE1", "Queen");
		tag(tags, "TALB", "News of the world");
		tag(tags, "TYER", "1977");
		ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
		mp3.writeBytes(new byte[] { 'I', 'D', '3', 3, 0, 0, 0, 0,
			(byte) (tags.size() >> 7), (byte) (tags.size() & 127) });
		mp3.writeBytes(tags.toByteArray());
		// MPEG-1 Layer III frames: 128 kbps, 44.1 kHz, stereo, zeroed audio payload.
		byte[] frame = new byte[417];
		frame[0] = (byte) 0xff;
		frame[1] = (byte) 0xfb;
		frame[2] = (byte) 0x90;
		for (int i = 0; i < 80; i++) {
			mp3.writeBytes(frame);
		}
		return mp3.toByteArray();
	}

	private static void tag(ByteArrayOutputStream output, String name, String value) {
		byte[] text = value.getBytes(StandardCharsets.ISO_8859_1);
		output.writeBytes(name.getBytes(StandardCharsets.US_ASCII));
		output.writeBytes(ByteBuffer.allocate(4).putInt(text.length + 1).array());
		output.writeBytes(new byte[] { 0, 0, 0 });
		output.writeBytes(text);
	}
}
