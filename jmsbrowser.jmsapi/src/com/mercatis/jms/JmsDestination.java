package com.mercatis.jms;

import java.io.Serializable;

public final class JmsDestination implements Serializable, Comparable<JmsDestination> {
	private static final long serialVersionUID = -2740275131332385926L;

	public static enum DestinationType { TOPIC, QUEUE };

	public final DestinationType type;
	public final String name;
	
	private static final char seperator = '.';
	
	
	public JmsDestination(DestinationType destinationType, String destinationName) {
		type = destinationType;
		name = destinationName;
	}

	public static String getTypeName(DestinationType type) {
		switch (type) {
			case QUEUE:
				return "Queue";
			case TOPIC:
				return "Topic";
		}
		return "<not impl>";
	}
	
	public static DestinationType getTypeFromName(String type) {
		String s = type.toLowerCase(); 
		if (s.equals("queue") || s.equals("q"))
			return DestinationType.QUEUE;
		if (s.equals("topic") || s.equals("t"))
			return DestinationType.TOPIC;
		return null;
	}
	
	@Override
	public String toString() {
		return getTypeName(type) + seperator + name;
	}
	
	@Override
	public JmsDestination clone() {
		return new JmsDestination(type, name);
	}

	@Override
	public int compareTo(JmsDestination other) {
		return name.compareTo(other.name);
	}
}
