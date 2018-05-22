package com.mercatis.jms;

import java.io.Serializable;

public class JmsDurableSubscription<T> implements Serializable, Comparable<JmsDurableSubscription<T>> {
	private static final long serialVersionUID = -370857010677842987L;

	public final String topic;
	public final String name;
	public final String clientID;
	public final String str; 
	public final T customData;
	
	public JmsDurableSubscription(String topic, String name, String clientID, T t) {
		this.topic = topic;
		this.name = name;
		this.clientID = clientID;
		this.str = clientID+" on "+topic+" ("+name+")";
		this.customData = t;
	}

	@Override
	public String toString() {
		return str;
	}
	
	@Override
	public JmsDurableSubscription<T> clone() {
		return new JmsDurableSubscription<T>(topic, name, clientID, customData);
	}

	@Override
	public int compareTo(JmsDurableSubscription<T> other) {
		return name.compareTo(other.name);
	}
}
