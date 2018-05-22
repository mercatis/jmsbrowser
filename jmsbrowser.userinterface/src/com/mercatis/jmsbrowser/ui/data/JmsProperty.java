package com.mercatis.jmsbrowser.ui.data;

import javax.jms.Destination;

import org.eclipse.swt.graphics.Color;

import com.mercatis.jms.JmsDestination;

public class JmsProperty {
	public String name;
	public String value;
	public Color color;
	public boolean deleteable;
	public boolean editable;
	
	public JmsProperty(String n, String v, Color c, boolean d, boolean e) {
		init(n, v, c, d, e);
	}
	public JmsProperty(String n, int i, Color c, boolean d, boolean e) {
		init(n, Integer.toString(i), c, d, e);
	}
	public JmsProperty(String n, long l, Color c, boolean d, boolean e) {
		init(n, new Long(l).toString(), c, d, e);
	}
	public JmsProperty(String n, JmsDestination d, Color c, boolean dd, boolean e) {
		init(n, d==null ? "" : d.name, c, dd, e);
	}
	public JmsProperty(String n, Destination d, Color c, boolean dd, boolean e) {
		init(n, d==null ? "" : d.toString(), c, dd, e);
	}
	private void init(String n, String v, Color c, boolean d, boolean e) {
		name = n;
		value = (v==null) ? "" : v;
		color = c;
		deleteable = d;
		editable = e;
	}
}

