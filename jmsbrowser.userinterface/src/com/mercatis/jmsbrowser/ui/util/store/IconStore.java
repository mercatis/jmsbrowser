package com.mercatis.jmsbrowser.ui.util.store;

import javax.jms.BytesMessage;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;

public class IconStore {
	private static class ImageDescriptors {
		static final ImageDescriptor message_text = ImageDescriptor.createFromFile(IconStore.class, "/icons/document-text.png");
		static final ImageDescriptor message_binary = ImageDescriptor.createFromFile(IconStore.class, "/icons/document-binary.png");
		static final ImageDescriptor message_object = ImageDescriptor.createFromFile(IconStore.class, "/icons/document-sub.png");
		static final ImageDescriptor message_stream = ImageDescriptor.createFromFile(IconStore.class, "/icons/document-list.png");
		static final ImageDescriptor message_save = ImageDescriptor.createFromFile(IconStore.class, "/icons/disk-black.png");
		static final ImageDescriptor message_save_all = ImageDescriptor.createFromFile(IconStore.class, "/icons/disks-black.png");
		static final ImageDescriptor message_load = ImageDescriptor.createFromFile(IconStore.class, "/icons/disk--arrow.png");
		static final ImageDescriptor message_delete = ImageDescriptor.createFromFile(IconStore.class, "/icons/disk--minus.png");
		static final ImageDescriptor message_dupe = ImageDescriptor.createFromFile(IconStore.class, "/icons/document-copy.png");
		static final ImageDescriptor message_resend = ImageDescriptor.createFromFile(IconStore.class, "/icons/paper-plane--arrow.png");
		
		static final ImageDescriptor folder_open = ImageDescriptor.createFromFile(IconStore.class, "/icons/folder-open.png");
		static final ImageDescriptor folder_closed = ImageDescriptor.createFromFile(IconStore.class, "/icons/folder.png");
	
		static final ImageDescriptor queue = ImageDescriptor.createFromFile(IconStore.class, "/icons/arrow-join.png");
		static final ImageDescriptor topic = ImageDescriptor.createFromFile(IconStore.class, "/icons/arrow-split.png");
		static final ImageDescriptor durable_subscriber = ImageDescriptor.createFromFile(IconStore.class, "/icons/anchor.png");
		
		static final ImageDescriptor connection_closed = ImageDescriptor.createFromFile(IconStore.class, "/icons/building.png");
		static final ImageDescriptor connection_established = ImageDescriptor.createFromFile(IconStore.class, "/icons/building-network.png");
		static final ImageDescriptor connection_connecting = ImageDescriptor.createFromFile(IconStore.class, "/icons/building--arrow.png");
		
		static final ImageDescriptor archive_add = ImageDescriptor.createFromFile(IconStore.class, "/icons/inbox-download.png");
		static final ImageDescriptor archive_move = ImageDescriptor.createFromFile(IconStore.class, "/icons/inbox--arrow.png");
		static final ImageDescriptor archive_closed = ImageDescriptor.createFromFile(IconStore.class, "/icons/inbox-document.png");
		
		static final ImageDescriptor generic_delete = ImageDescriptor.createFromFile(IconStore.class, "/icons/cross-script.png");
		static final ImageDescriptor generic_refresh = ImageDescriptor.createFromFile(IconStore.class, "/icons/arrow-circle-double-135.png");
		static final ImageDescriptor generic_refresh_simple = ImageDescriptor.createFromFile(IconStore.class, "/icons/arrow-repeat.png");
		static final ImageDescriptor generic_rename = ImageDescriptor.createFromFile(IconStore.class, "/icons/edit-language.png");
		
		static final ImageDescriptor dest_monitor = ImageDescriptor.createFromFile(IconStore.class, "/icons/eye.png");
		static final ImageDescriptor dest_purge = ImageDescriptor.createFromFile(IconStore.class, "/icons/bin-metal-full.png");

		static final ImageDescriptor properties_edit = ImageDescriptor.createFromFile(IconStore.class, "/icons/gear--pencil.png");
		
		static final ImageDescriptor payload_clear = ImageDescriptor.createFromFile(IconStore.class, "/icons/envelope--minus.png");
		static final ImageDescriptor payload_load = ImageDescriptor.createFromFile(IconStore.class, "/icons/envelope--plus.png");
		static final ImageDescriptor payload_save = ImageDescriptor.createFromFile(IconStore.class, "/icons/envelope-share.png");

		static final ImageDescriptor log_info = ImageDescriptor.createFromFile(IconStore.class, "/icons/exclamation-white.png");
		static final ImageDescriptor log_warn = ImageDescriptor.createFromFile(IconStore.class, "/icons/exclamation--frame.png");
		static final ImageDescriptor log_error = ImageDescriptor.createFromFile(IconStore.class, "/icons/exclamation-red-frame.png");
	
		static final ImageDescriptor filter_tstamp = ImageDescriptor.createFromFile(IconStore.class, "/icons/clock-small.png");
		static final ImageDescriptor filter_props = ImageDescriptor.createFromFile(IconStore.class, "/icons/foaf.png");
		static final ImageDescriptor filter_payload = ImageDescriptor.createFromFile(IconStore.class, "/icons/paper-clip-small.png");
	}
	
	public static final Image message_text = ImageDescriptors.message_text.createImage();
	public static final Image message_binary = ImageDescriptors.message_binary.createImage();
	public static final Image message_object = ImageDescriptors.message_object.createImage();
	public static final Image message_stream = ImageDescriptors.message_stream.createImage();
	public static final Image message_save = ImageDescriptors.message_save.createImage();
	public static final Image message_save_all = ImageDescriptors.message_save_all.createImage();
	public static final Image message_load = ImageDescriptors.message_load.createImage();
	public static final Image message_delete = ImageDescriptors.message_delete.createImage();
	public static final Image message_dupe = ImageDescriptors.message_dupe.createImage();
	public static final Image message_resend = ImageDescriptors.message_resend.createImage();
	
	public static final Image folder_open = ImageDescriptors.folder_open.createImage();
	public static final Image folder_closed = ImageDescriptors.folder_closed.createImage();

	public static final Image queue = ImageDescriptors.queue.createImage();
	public static final Image topic = ImageDescriptors.topic.createImage();
	public static final Image durable_subscriber = ImageDescriptors.durable_subscriber.createImage();
	
	public static final Image connection_closed = ImageDescriptors.connection_closed.createImage();
	public static final Image connection_established = ImageDescriptors.connection_established.createImage();
	public static final Image connection_connecting = ImageDescriptors.connection_connecting.createImage();
	
	public static final Image archive_add = ImageDescriptors.archive_add.createImage();
	public static final Image archive_move = ImageDescriptors.archive_move.createImage();
	public static final Image archive_closed = ImageDescriptors.archive_closed.createImage();
	
	public static final Image generic_delete = ImageDescriptors.generic_delete.createImage();
	public static final Image generic_refresh = ImageDescriptors.generic_refresh.createImage();
	public static final Image generic_refresh_simple = ImageDescriptors.generic_refresh_simple.createImage();
	public static final Image generic_rename = ImageDescriptors.generic_rename.createImage();
	
	public static final Image dest_monitor = ImageDescriptors.dest_monitor.createImage();
	public static final Image dest_purge = ImageDescriptors.dest_purge.createImage();
	
	public static final Image properties_edit = ImageDescriptors.properties_edit.createImage();

	public static final Image payload_clear = ImageDescriptors.payload_clear.createImage();
	public static final Image payload_load = ImageDescriptors.payload_load.createImage();
	public static final Image payload_save = ImageDescriptors.payload_save.createImage();

	public static final Image log_info = ImageDescriptors.log_info.createImage();
	public static final Image log_warn = ImageDescriptors.log_warn.createImage();
	public static final Image log_error = ImageDescriptors.log_error.createImage();
	
	public static final Image filter_tstamp = ImageDescriptors.filter_tstamp.createImage();
	public static final Image filter_props = ImageDescriptors.filter_props.createImage();
	public static final Image filter_payload = ImageDescriptors.filter_payload.createImage();
	
	public static Image getMessageIcon(Object msg) {
		if (msg instanceof TextMessage)
			return message_text;
		if (msg instanceof BytesMessage)
			return message_binary;
		if (msg instanceof ObjectMessage)
			return message_object;
		if (msg instanceof StreamMessage)
			return message_stream;
		return null;
	}
}
