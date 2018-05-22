package com.mercatis.jms.log;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.graphics.Image;
import com.mercatis.jms.log.LogEntry.MsgType;

public final class LogUtil {
	private static LogListener listener;
	private static IStatusLineManager slm;
	private static LinkedList<LogEntry> logs = new LinkedList<LogEntry>();
	
	private static Image log_info;
	private static Image log_warn;
	private static Image log_error;
	
	private static synchronized void log(MsgType type, String msg, Throwable t) {
		LogEntry le = new LogEntry(type, msg, t);
		logs.add(0, le);
		showMessage(le);
		if (listener != null)
			listener.onLogEntry(le);
	}
	
	private static void showMessage(LogEntry le) {
		if (slm==null || le==null)
			return;
		switch(le.type) {
			case FATAL:
			case ERROR:
				slm.setErrorMessage(log_error, le.toString());
				return;
			case WARNING:
				slm.setErrorMessage(null);
				slm.setMessage(log_warn, le.toString());
				return;
			default:
				slm.setErrorMessage(null);
				slm.setMessage(log_info, le.toString());
		}
	}

	public static void logError(String s, Throwable t) {
		log(MsgType.ERROR, s, t);
	}
	
	public static void logWarn(String s) {
		log(MsgType.WARNING, s, null);
	}
	
	public static void logInfo(String s) {
		log(MsgType.INFO, s, null);
	}

	public static List<LogEntry> getLogMessages() {
		return Collections.unmodifiableList(logs);
	}
	
	public static void setListener(LogListener listener) {
		LogUtil.listener = listener;
	}

	public static void refresh(IStatusLineManager islm) {
		slm = islm;
		if (logs.size()>0)
			showMessage(logs.get(0));
	}

	public static void refresh(IStatusLineManager islm, Image info, Image warn, Image error) {
		log_info = info;
		log_warn = warn;
		log_error = error;
		refresh(islm);
	}
}
