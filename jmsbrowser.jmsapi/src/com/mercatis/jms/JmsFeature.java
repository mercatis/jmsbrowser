package com.mercatis.jms;

public final class JmsFeature {
	public static final long DESTINATION_CREATE = 0x1;
	public static final long DESTINATION_DELETE = 0x2;
	public static final long DURABLES_LIST = 0x4;
	public static final long DURABLES_COUNT = 0x8;
	public static final long DURABLES_PURGE = 0x10;
	
	// HELPER VALUES
	public static final long NONE = 0;
	public static final long DESTINATION_ALL = DESTINATION_CREATE | DESTINATION_DELETE;
	public static final long DURABLES_ALL = DURABLES_LIST | DURABLES_COUNT | DURABLES_PURGE;
	
	public static boolean isSupported(JmsConnection conn, long feature) {
		return (conn.getSupportedFeatures() & feature) > 0;
	}
}
