package com.mercatis.jmsbrowser.ui.util;

public class StackTraceFormatter {
	
	private static final String NEW_LINE = System.getProperty("line.separator");

	private String reasonString;
	private final String stacktrace;
	
	public StackTraceFormatter(String prefix, Throwable t) {
		stacktrace = formatStacktrace(prefix, t);
	}

	public StackTraceFormatter(Throwable t) {
		this(null, t);
	}

	private String formatStacktrace(String prefix, Throwable throwable) {
		final StringBuilder sb = new StringBuilder();
		if (prefix!=null) {
			sb.append(prefix);
		}
		if (throwable !=null) {
			if (prefix!=null)
				sb.append(NEW_LINE).append(NEW_LINE);
			Throwable th = throwable;
			do {
				reasonString = th.getMessage();
				sb.append(th.getClass().getCanonicalName());
				sb.append(" - ").append(th.getMessage());
				for (StackTraceElement ste : th.getStackTrace()) {
					if (ste.getClassName().startsWith("com.mercatis.jmsbrowser.ui.")) {
						// hide ui exceptions and everything after
						break;
					}
					sb.append(NEW_LINE).append('\t').append(ste);
					System.out.println(ste);
				}
				sb.append(NEW_LINE);
				th = th.getCause();
			} while(th!=null);
		}
		return sb.toString();
	}

	public String getReasonString() {
		return reasonString;
	}

	public String getStacktrace() {
		return stacktrace;
	}
}
