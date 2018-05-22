package com.mercatis.jmsbrowser.ui.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.TableItem;

import com.mercatis.jms.JmsDestination;
import com.mercatis.jms.JmsDestination.DestinationType;
import com.mercatis.jmsbrowser.ui.model.ArchivedMessage;
import com.mercatis.jmsbrowser.ui.util.store.ColorStore;

public class MessageTableProvider implements ITableLabelProvider, IStructuredContentProvider, IColorProvider, ICellModifier {

	private List<JmsProperty> entries = new LinkedList<>();
	private ArchivedMessage msg;
	private TableViewer tv;
	private Set<ILabelProviderListener> listeners = new HashSet<>();
	private boolean withoutEditor = true;
	private static SimpleDateFormat dateFormater = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");

	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		return null;
	}

	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof JmsProperty) {
			JmsProperty jp = (JmsProperty) element;
			switch (columnIndex) {
				case 0:
					return jp.name;
				case 1:
					return jp.value;
			}
		}
		return null;
	}

	@Override
	public void addListener(ILabelProviderListener listener) {
		listeners.add(listener);
	}

	@Override
	public void dispose() {
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		listeners.remove(listener);
	}

	@Override
	public Object[] getElements(Object inputElement) {
		return entries.toArray();
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		tv = (TableViewer) viewer;
		tv.remove(entries.toArray());
		entries.clear();

		if (newInput == null)
			return;
		if (newInput instanceof Message) {
			setMessage((Message) newInput);
		}

		if (newInput instanceof ArchivedMessage) {
			setMessage((ArchivedMessage) newInput);
		}
	}

	public void setMessage(ArchivedMessage message) {
		msg=message;
		withoutEditor = false;
		tv.remove(entries.toArray());
		entries.clear();
		if (message == null)
			return;
		// label
		entries.add(new JmsProperty("Label", message.getLabel(), ColorStore.green, false, true));
		// read only values
		entries.add(new JmsProperty("Filename", message.getFilename(), ColorStore.darkred, false, false));
		entries.add(new JmsProperty("Message Type", message.getMessageType(), ColorStore.darkred, false, false));
		entries.add(new JmsProperty("Message ID", message.getMessageID(), null, false, false));
		entries.add(new JmsProperty("Timestamp", dateFormater.format(new Date(message.getTimestamp())), null, false, false));
		entries.add(new JmsProperty("Destination", message.getDestination()==null ? "" : message.getDestination().toString(), null, false, false));
		// default entries, read-write
		entries.add(new JmsProperty("Correlation ID", message.getCorrelationID(), null, false, true));
		entries.add(new JmsProperty("Delivery Mode", message.getDeliveryMode(), null, false, true));
		entries.add(new JmsProperty("Expiration", message.getExpiration(), null, false, true));
		entries.add(new JmsProperty("Priority", message.getPriority(), null, false, true));
		entries.add(new JmsProperty("Reply To", message.getReplyTo(), null, false, true));
		entries.add(new JmsProperty("Type", message.getType(), null, false, true));
		Set<String> propEnum = message.getPropertyNames();
		for (String s : propEnum) {
			entries.add(new JmsProperty(s, message.getProperty(s), ColorStore.blue, true, true));
		}
		entries.add(new JmsProperty("", "", ColorStore.blue, false, true));
		tv.add(entries.toArray());
		fireListener(message);
	}

	private void setMessage(Message message) {
		withoutEditor = true;
		tv.remove(entries.toArray());
		entries.clear();
		if (message == null)
			return;
		try {
			entries.add(new JmsProperty("Java Class", message.getClass().getName(), ColorStore.darkred, false, false));
			entries.add(new JmsProperty("Message ID", message.getJMSMessageID(), null, false, false));
			entries.add(new JmsProperty("Correlation ID", message.getJMSCorrelationID(), null, false, false));
			entries.add(new JmsProperty("Delivery Mode", translateDeliveryMode(message.getJMSDeliveryMode()), null, false, false));
			entries.add(new JmsProperty("Expiration", message.getJMSExpiration(), null, false, false));
			entries.add(new JmsProperty("Priority", message.getJMSPriority(), null, false, false));
			entries.add(new JmsProperty("Reply To", message.getJMSReplyTo(), null, false, false));
			entries.add(new JmsProperty("Timestamp", dateFormater.format(new Date(message.getJMSTimestamp())), null, false, false));
			entries.add(new JmsProperty("Type", message.getJMSType(), null, false, false));
			entries.add(new JmsProperty("Destination", message.getJMSDestination(), null, false, false));
			Enumeration<?> propEnum = message.getPropertyNames();
			while (propEnum.hasMoreElements()) {
				String prop = (String) propEnum.nextElement();
				entries.add(new JmsProperty(prop, message.getStringProperty(prop), ColorStore.blue, false, true));
			}
		} catch (JMSException e) {
			entries.clear();
			entries.add(new JmsProperty("<error>", e.getMessage(), ColorStore.darkred, false, false));
			e.printStackTrace();
		}
		tv.add(entries.toArray());
	}

	private String translateDeliveryMode(int mode) {
		switch (mode) {
			case DeliveryMode.PERSISTENT:
				return "PERSISTENT";
			case DeliveryMode.NON_PERSISTENT:
				return "NON_PERSISTENT";
			default:
				return "<unknown>";
		}
	}

	@Override
	public Color getBackground(Object element) {
		return null;
	}

	@Override
	public Color getForeground(Object element) {
		JmsProperty prop = (JmsProperty) element;
		if (withoutEditor || prop.editable)
			return prop.color;
		return ColorStore.darkgrey;
	}

	@Override
	public boolean canModify(Object element, String property) {
		int idx = entries.indexOf(element);
		if(idx==entries.size()-1)
			return property.equals("PROPERTY");
		return property.equals("VALUE") && ((JmsProperty) element).editable;
	}

	@Override
	public Object getValue(Object element, String property) {
		return ((JmsProperty)element).value;
	}

	@Override
	public void modify(Object element, String property, Object value) {
		TableItem item = (TableItem) element;
		JmsProperty prop = (JmsProperty) item.getData();
		String s = (String) value;
		if (property.equals("PROPERTY") && s.length()>0) {
			prop.name = s;
			prop.deleteable = true;
			msg.setProperty(prop.name, prop.value);
			// add empty line at bottom
			JmsProperty prop2 = new JmsProperty("", "", ColorStore.blue, false, true);
			entries.add(prop2);
			tv.add(prop2);
		} else if (!s.equals(prop.value)) {
			prop.value = s;
			try {
				switch (entries.indexOf(prop)) {
					case 0:
						msg.setLabel(prop.value);
						break;
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						break;
					case 6:
						msg.setCorrelationID(prop.value);
						break;
					case 7:
						msg.setDeliveryMode(Integer.parseInt(prop.value));
						break;
					case 8:
						msg.setExpiration(Long.parseLong(prop.value));
						break;
					case 9:
						msg.setPriority(Integer.parseInt(prop.value));
						break;
					case 10:
						msg.setReplyTo(new JmsDestination(DestinationType.QUEUE, prop.value));
						break;
					case 11:
						msg.setType(prop.value);
						break;
					default:
						msg.setProperty(prop.name, prop.value);
				}
			} catch (NumberFormatException e) {
				MessageDialog.openError(tv.getControl().getShell(), "Invalid Value", "Invalid value \"" + prop.value + "\" for property " + prop.name + ".");
				return;
			}
		} else
			return;
		tv.update(prop, null);
		fireListener(msg);
	}

	public void fireListener(ArchivedMessage msg) {
		LabelProviderChangedEvent event = new LabelProviderChangedEvent(this, msg);
		for (ILabelProviderListener l : listeners)
			l.labelProviderChanged(event);
	}
}
