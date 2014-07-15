/*
    Copyright (C) 2014 Sergii Pylypenko.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.cups.android;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.EditText;
import android.text.Editable;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.graphics.drawable.Drawable;
import android.graphics.Color;
import android.content.res.Configuration;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.view.View.OnKeyListener;
import android.view.MenuItem;
import android.view.Menu;
import android.view.Gravity;
import android.text.method.TextKeyListener;
import java.util.LinkedList;
import java.io.SequenceInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.zip.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Set;
import android.text.SpannedString;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import android.view.inputmethod.InputMethodManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import java.util.concurrent.Semaphore;
import android.content.pm.ActivityInfo;
import android.view.Display;
import android.text.InputType;
import android.util.Log;
import android.view.Surface;
import android.app.ProgressDialog;
import java.util.ArrayList;
import android.print.*;
import android.printservice.*;
import java.util.*;


public class CupsPrintService extends PrintService
{
	@Override void onConnected()
	{
		Log.d(TAG, "onConnected()");
		super.onConnected();
		if (!isInstalled(p))
		{
			Intent dialogIntent = new Intent(getBaseContext(), MainActivity.class);
			dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			getApplication().startActivity(dialogIntent);
		}
		else
		{
			startCupsDaemon(this);
		}
	}

	@Override void onDisconnected()
	{
		Log.d(TAG, "onDisconnected()");
		super.onDisconnected();
		stopCupsDaemon(this);
	}

	class CupsPrinterDiscoverySession extends PrinterDiscoverySession
	{
		public void onDestroy()
		{
			Log.d(TAG, "onDestroy()");
		}
		public void onStartPrinterDiscovery(List<PrinterId> priorityList)
		{
			Log.d(TAG, "onStartPrinterDiscovery()");
			ArrayList<PrinterInfo> ret = new ArrayList<PrinterInfo>();
			String[] printers = Cups.getPrinters(CupsPrintService.this);
			for (String pr: printers)
			{
				PrinterId id = CupsPrintService.generatePrinterId(pr);
				PrinterInfo.Builder pi = new PrinterInfo.Builder(id, pr, Cups.getPrinterStatus(CupsPrintService.this, pr));
				pi.setDescription("");
				pi.setName(pr);
				PrinterCapabilitiesInfo.Builder pc = new PrinterCapabilitiesInfo.Builder(id);
				Map<String, String[]> options = getPrinterOptions(CupsPrintService.this, pr);
				if (options.containsKey("PageSize"))
				{
					String pagesize[] = options.get("PageSize");
					if (pagesize.length > 0 && Cups.getMediaSize(CupsPrintService.this, pagesize[0]) != null)
						pc.addMediaSize(Cups.getMediaSize(CupsPrintService.this, pagesize[0]), true);
					for (int i = 1; i < pagesize.length; i++)
						if (Cups.getMediaSize(CupsPrintService.this, pagesize[i]) != null)
							pc.addMediaSize(Cups.getMediaSize(CupsPrintService.this, pagesize[i]), false);
				}
				if (options.containsKey("Resolution"))
				{
					String res[] = options.get("Resolution");
					if (res.length > 0)
						pc.addResolution(Cups.getResolution(res[0]));
					for (int i = 1; i < res.length; i++)
						pc.addResolution(Cups.getResolution(res[i]));
				}
				pi.setCapabilities(pc.build());
				ret.add(pi.build());
			}
			addPrinters(ret);
		}
		public void onStartPrinterStateTracking(PrinterId id)
		{
			ArrayList<PrinterInfo> ret = new ArrayList<PrinterInfo>();
			String pr = id.getLocalId();
			Log.d(TAG, "onStartPrinterTracking(): " + id);
			PrinterInfo.Builder pi = new PrinterInfo.Builder(id, pr, Cups.getPrinterStatus(CupsPrintService.this, pr));
			pi.setDescription("");
			pi.setName(pr);
			PrinterCapabilitiesInfo.Builder pc = new PrinterCapabilitiesInfo.Builder(id);
			Map<String, String[]> options = getPrinterOptions(CupsPrintService.this, pr);
			if (options.containsKey("PageSize"))
			{
				String pagesize[] = options.get("PageSize");
				if (pagesize.length > 0 && Cups.getMediaSize(CupsPrintService.this, pagesize[0]) != null)
					pc.addMediaSize(Cups.getMediaSize(CupsPrintService.this, pagesize[0]), true);
				for (int i = 1; i < pagesize.length; i++)
					if (Cups.getMediaSize(CupsPrintService.this, pagesize[i]) != null)
						pc.addMediaSize(Cups.getMediaSize(CupsPrintService.this, pagesize[i]), false);
			}
			if (options.containsKey("Resolution"))
			{
				String res[] = options.get("Resolution");
				if (res.length > 0)
					pc.addResolution(Cups.getResolution(res[0]));
				for (int i = 1; i < res.length; i++)
					pc.addResolution(Cups.getResolution(res[i]));
			}
			pi.setCapabilities(pc.build());
			ret.add(pi.build());
			addPrinters(ret);
		}
		public void onStopPrinterDiscovery()
		{
			// TODO: implement that
		}
		public void onStopPrinterStateTracking(PrinterId printerId)
		{
			// TODO: implement that
		}
		public void onValidatePrinters(List<PrinterId> printerIds)
		{
			Log.d(TAG, "onValidatePrinters()");
			ArrayList<PrinterInfo> ret = new ArrayList<PrinterInfo>();
			for (PrinterId id: printerIds)
			{
				String pr = id.getLocalId();
				PrinterInfo.Builder pi = new PrinterInfo.Builder(id, pr, Cups.getPrinterStatus(CupsPrintService.this, pr));
				ret.add(pi.build());
			}
			addPrinters(ret);
		}

		public static final String TAG = "CupsPrinterDiscoverySession";
	}

	@Override PrinterDiscoverySession onCreatePrinterDiscoverySession()
	{
		Log.d(TAG, "onCreatePrinterDiscoverySession()");
		return new CupsPrinterDiscoverySession();
	}

	@Override void onPrintJobQueued(android.printservice.PrintJob printJob)
	{
		Log.d(TAG, "onPrintJobQueued()");
	}

	@Override void onRequestCancelPrintJob(android.printservice.PrintJob printJob)
	{
		Log.d(TAG, "onRequestCancelPrintJob()");
	}

	@Override IBinder onBind(Intent intent)
	{
		return binder;
	}

	private final IBinder binder = new Binder() {
		@Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException
		{
			return super.onTransact(code, data, reply, flags);
		}
	};
	static public final String TAG = "CupsPrintService";
}
