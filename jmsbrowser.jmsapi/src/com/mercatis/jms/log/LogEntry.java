package com.mercatis.jms.log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class LogEntry {
	private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
	public final long timeStamp;
	public final MsgType type;
	public final String msg;
	public final Throwable throwable;
	private String cached;

	public enum MsgType {
		FATAL("FATAL"),
		ERROR("ERROR"),
		WARNING("WARNING"),
		INFO("INFO");

		private MsgType(final String s) {
			this.s = s;
		}
		private final String s;

		@Override
		public String toString() {
			return this.s;
		}
	}

	public LogEntry (final MsgType type, final String msg, final Throwable t) {
		this.timeStamp = System.currentTimeMillis();
		this.type = type;
		this.msg = msg;
		this.throwable = t;
	}

	@Override
	public String toString() {
		if (this.cached == null) {
			final StringBuilder sb = new StringBuilder();
			sb.append(DATE_FORMAT.format(new Date(this.timeStamp)));
			sb.append(" ");
			sb.append(this.msg);
			this.cached = sb.toString();
		}
		return this.cached;
	}
}
