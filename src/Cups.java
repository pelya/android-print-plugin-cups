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
import android.print.*;
import android.printservice.*;
import java.util.*;
import java.io.*;
import android.os.Environment;
import android.os.StatFs;
import java.net.URL;
import android.net.Uri;


public class Cups
{
	static String IMG = "/img";
	static String PROOT = "./proot.sh";
	static String CUPSD = "/usr/sbin/cupsd";
	static String LP = "/usr/bin/lp";
	static String LPSTAT = "/usr/bin/lpstat";
	static String LPOPTIONS = "/usr/bin/lpoptions";
	static String LPINFO = "/usr/sbin/lpinfo";
	static String LPADMIN = "/usr/sbin/lpadmin";
	static String CANCEL = "/usr/bin/cancel";
	static String CUPSACCEPT = "/usr/sbin/cupsaccept";
	static String CUPSENABLE = "/usr/sbin/cupsenable";
	static String DBUS = "/usr/bin/dbus-daemon";
	static Process cupsd = null;
	static Process dbus = null;

	public static final double PointsToMillimeters = 0.35277777778;
	public static final double MillimetersToPoints = 1.0 / PointsToMillimeters;
	public static final double MillimetersToInches = 0.03937007874;

	public static File chrootPath(Context p)
	{
		return new File(p.getFilesDir().getAbsolutePath() + IMG);
	}

	synchronized public static boolean isRunning(Context p)
	{
		Proc pp = new Proc(new String[] {PROOT, LPSTAT, "-r"}, chrootPath(p));
		return pp.out.length > 0 && pp.out[0].equals("scheduler is running");
	}

	private static String[] printers = null;
	private static HashSet<String> printerIsBusy = new HashSet<String>();
	private static HashMap<String, HashMap<String, String[]> > printerOptions = new HashMap<String, HashMap<String, String[]> >();
	
	synchronized private static void setPrinterList(String [] _printers, HashSet<String> _printerIsBusy, HashMap<String, HashMap<String, String[]> > _printerOptions)
	{
		printers = _printers;
		printerIsBusy = _printerIsBusy;
		printerOptions = _printerOptions;
	}

	public static void updatePrintersInfo(Context p)
	{
		ArrayList<String> printerList = new ArrayList<String>();
		Proc pp = new Proc(new String[] {PROOT, LPSTAT, "-v"}, chrootPath(p));
		for (String s: pp.out)
		{
			if (!s.startsWith("device for ") || s.indexOf(":") == -1)
				continue;
			printerList.add(s.substring(("device for ").length(), s.indexOf(":")));
		}

		HashSet<String> busy = new HashSet<String>();
		for(String printer: printerList)
		{
			pp = new Proc(new String[] {PROOT, LPSTAT, "-p", printer}, chrootPath(p));
			if (pp.out.length == 0 || pp.status != 0)
				continue;
			if (pp.out[0].indexOf("is idle") == -1)
				busy.add(printer);
		}

		HashMap<String, HashMap<String, String[]> > allOptions = new HashMap<String, HashMap<String, String[]> >();
		for(String printer: printerList)
		{
			pp = new Proc(new String[] {PROOT, LPOPTIONS, "-p", printer, "-l"}, chrootPath(p));
			if (pp.out.length == 0 || pp.status != 0)
				continue;
			HashMap<String, String[]> options = new HashMap<String, String[]>();
			for(String s: pp.out)
			{
				if (s.indexOf("/") == -1 || s.indexOf(": ") == -1)
					continue;
				String k = s.substring(0, s.indexOf("/"));
				String vv[] = s.substring(s.indexOf(": ") + 2).split("\\s+");
				for (int i = 0; i < vv.length; i++)
				{
					if (vv[i].startsWith("*"))
					{
						String dd = vv[i].substring(1);
						vv[i] = vv[0];
						vv[0] = dd;
						break;
					}
				}
				options.put(k, vv);
			}
			allOptions.put(printer, options);
		}

		setPrinterList(printerList.toArray(new String[0]), busy, allOptions);
	}

	synchronized public static String[] getPrinters(Context p)
	{
		if (printers == null)
			updatePrintersInfo(p);
		return printers;
	}

	synchronized public static int getPrinterStatus(Context p, String printer)
	{
		if (printers == null)
			updatePrintersInfo(p);
		if (!Arrays.asList(printers).contains(printer))
			return PrinterInfo.STATUS_UNAVAILABLE;
		if (!printerIsBusy.contains(printer))
			return PrinterInfo.STATUS_IDLE;
		return PrinterInfo.STATUS_BUSY;
	}

	synchronized public static Map<String, String[]> getPrinterOptions(Context p, String printer)
	{
		if (printers == null)
			updatePrintersInfo(p);
		if (printerOptions.containsKey(printer))
			return printerOptions.get(printer);
		return new HashMap<String, String[]>();
	}

	synchronized public static Map<String, String[]> getPrintJobs(Context p, String printer, boolean completedJobs)
	{
		HashMap<String, String[]> ret = new HashMap<String, String[]>();
		Proc pp;
		if (completedJobs)
			pp = new Proc(new String[] {PROOT, LPSTAT, "-W", "completed", "-l", printer}, chrootPath(p));
		else
			pp = new Proc(new String[] {PROOT, LPSTAT, "-l", printer}, chrootPath(p));
		if (pp.out.length == 0 || pp.status != 0)
			return ret;
		String currentJob = null;
		ArrayList<String> jobAttrs = new ArrayList();
		for (String s: pp.out)
		{
			if (s.trim().length() == 0)
				continue;
			if (s.startsWith(" ") || s.startsWith("\t"))
			{
				jobAttrs.add(s.trim());
			}
			else
			{
				if (currentJob != null)
					ret.put(currentJob, jobAttrs.toArray(new String[0]));
				currentJob = s.split("\\s+")[0];
				jobAttrs = new ArrayList();
			}
		}
		if (currentJob != null)
			ret.put(currentJob, jobAttrs.toArray(new String[0]));
		return ret;
	}

	synchronized public static Uri getPrinterAddress(Context p, String printer)
	{
		ArrayList<String> printerList = new ArrayList<String>();
		Proc pp = new Proc(new String[] {PROOT, LPSTAT, "-v", printer}, chrootPath(p));
		String addr = null;
		for (String s: pp.out)
		{
			if (!s.startsWith("device for ") || s.indexOf(":") == -1)
				continue;
			addr = s.substring(s.indexOf(":") + 1);
			break;
		}
		if (addr == null)
		{
			Log.d(TAG, "getPrinterAddress: addr == null");
			return null;
		}
		addr = addr.trim();
		Log.d(TAG, "getPrinterAddress: addr = " + addr);
		if (!addr.startsWith("smb://"))
		{
			Log.d(TAG, "getPrinterAddress: addr.startsWith(smb://)");
			return null;
		}
		addr = addr.substring("smb://".length());
		String[] parts = addr.split("/");
		Uri.Builder uri = new Uri.Builder();
		uri.scheme(p.getResources().getString(R.string.add_printer_scheme));
		uri.authority(p.getResources().getString(R.string.add_printer_host));
		uri.appendQueryParameter("n", printer);
		if (parts.length >= 3)
		{
			uri.appendQueryParameter("d", parts[0]);
			uri.appendQueryParameter("s", parts[1]);
			uri.appendQueryParameter("p", parts[2]);
		}
		else if (parts.length == 2)
		{
			uri.appendQueryParameter("s", parts[0]);
			uri.appendQueryParameter("p", parts[1]);
		}
		else
		{
			Log.d(TAG, "getPrinterAddress: parts.length < 2");
			return null;
		}

		pp = new Proc(new String[] {PROOT, LPOPTIONS, "-p", printer}, chrootPath(p));
		String model = null;
		String MODEL_STR = "printer-make-and-model='";
		for (String s: pp.out)
		{
			if (s.indexOf(MODEL_STR) == -1)
				continue;
			String model1 = s.substring(s.indexOf(MODEL_STR) + MODEL_STR.length());
			if (model1.indexOf("'") == -1)
				continue;
			model = model1.substring(0, model1.indexOf("'"));
			break;
		}
		if (model == null)
		{
			Log.d(TAG, "getPrinterAddress: model == null");
			return null;
		}
		uri.appendQueryParameter("m", model);

		return uri.build();
	}

	synchronized public static void cancelPrintJob(Context p, String job)
	{
		Proc pp = new Proc(new String[] {PROOT, CANCEL, job}, chrootPath(p));
		Log.d(TAG, "Cancel job status: " + pp.status + " output: " + Arrays.toString(pp.out));
	}

	synchronized public static void enablePrinter(Context p, String printer)
	{
		Proc pp = new Proc(new String[] {PROOT, CUPSACCEPT, printer}, chrootPath(p));
		Log.d(TAG, "cupsaccept printer status: " + pp.status + " output: " + Arrays.toString(pp.out));
		pp = new Proc(new String[] {PROOT, CUPSENABLE, printer}, chrootPath(p));
		Log.d(TAG, "cupsenable printer status: " + pp.status + " output: " + Arrays.toString(pp.out));
	}

	private static Map<String, PrintAttributes.MediaSize> mediaSizes = null;

	synchronized public static PrintAttributes.MediaSize getMediaSize(Context p, String name)
	{
		if (mediaSizes == null)
			fillMediaSizes(p);
		return mediaSizes.get(name);
	}

	private static void fillMediaSizes(Context p)
	{
		mediaSizes = new HashMap<String, PrintAttributes.MediaSize>();
		try
		{
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(
				chrootPath(p).getAbsolutePath() + "/usr/share/cups/ppdc/media.defs")));
			String line;
			while((line = in.readLine()) != null)
			{
				if (!line.startsWith("#media \"") || line.indexOf("/") == -1)
					continue;
				line = line.replace("\\\"", "”");
				int slash = line.indexOf("/");
				String name = line.substring("#media \"".length(), slash);
				String descr = line.substring(slash + 1, line.indexOf("\"", slash + 1));
				String sizes[] = line.substring(line.indexOf("\"", slash + 1) + 1).trim().split("\\s+");
				//Log.d(TAG, "fillMediaSizes: dimensions: " + Arrays.toString(sizes) + " for " + name);
				if (sizes.length < 2)
					continue;
				int w = (int)Math.round(Integer.parseInt(sizes[0]) * PointsToMillimeters * MillimetersToInches * 1000.0f);
				int h = (int)Math.round(Integer.parseInt(sizes[1]) * PointsToMillimeters * MillimetersToInches * 1000.0f);
				Log.d(TAG, "fillMediaSizes: " + name + " desc '" + descr + "' size " + w + "x" + h + " inches/1000" );
				mediaSizes.put(name, new PrintAttributes.MediaSize(name, descr, w, h));
			}
			in.close();
		}
		catch(Exception e)
		{
			Log.i(TAG, "Error reading media sizes: " + e.toString());
		}
	}

	public static PrintAttributes.Resolution getResolution(String s)
	{
		String rr[] = s.split("[^0-9]+");
		if (rr.length == 0)
			return new PrintAttributes.Resolution(s, s, 300, 300);
		if (rr.length == 1)
			return new PrintAttributes.Resolution(s, s, Integer.parseInt(rr[0]), Integer.parseInt(rr[0]));
		return new PrintAttributes.Resolution(s, s, Integer.parseInt(rr[0]), Integer.parseInt(rr[1]));
	}

	synchronized public static void addPrinter(Context p, String name, String host, String printer, String model, String workgroup, String username, String password)
	{
		// TODO: user password will be accessbile through /proc filesystem to all processes, for several seconds while the command is executing
		// lpadmin does not provide any other convenient way of passing passwords though, and I don't want to mess up with lpoptions
		name = name.trim().replaceAll("[ 	/#]", "-");
		host = host.trim();
		printer = printer.trim();
		model = model.trim();
		workgroup = workgroup.trim();
		username = username.trim();
		password = password.trim();
		String url = "smb://";
		if (username.length() > 0 && password.length() > 0)
			url = url + username + ":" + password + "@";
		if (workgroup.length() > 0)
			url = url + workgroup + "/";
		url = url + host + "/";
		url = url + printer;
		Proc pp = new Proc(new String[] {PROOT, LPADMIN, "-p", name, "-v", url, "-m", model, "-o", "printer-error-policy=retry-job", "-E"}, chrootPath(p));
		Log.d(TAG, "Add printer status: " + pp.status + " output: " + Arrays.toString(pp.out));
	}

	synchronized public static void deletePrinter(Context p, String name)
	{
		new Proc(new String[] {PROOT, LPADMIN, "-x", name}, chrootPath(p));
	}

	synchronized public static String[] printDocument(	final Context p,
														final android.printservice.PrintJob job,
														final String printer,
														final String jobLabel,
														int copies,
														final String mediaSize,
														boolean landscape,
														final String resolution,
														final Options.DoubleSided doubleSided,
														final PageRange[] pages )
	{
		updateDns(p);
		final String[] ret = new String[] { "", "" };
		final String PIPE = "document.pdf";
		File pipeFile = new File(chrootPath(p), PIPE);
		pipeFile.delete();
		OutputStream out = null;
		try
		{
			Log.d(TAG, "Printing document: copying data to " + PIPE);
			out = new FileOutputStream(pipeFile);
			// We have to call job.getDocument().getData() right before we're starting to read from this file, otherwise we'll get crash inside PrintSpoolerService
			InputStream in = new FileInputStream(job.getDocument().getData().getFileDescriptor());
			int len = copyStream(in, out);
			Log.d(TAG, "Printing document: finished copying data to pipe: " + len + " bytes");
		}
		catch(Exception e)
		{
			Log.i(TAG, "Error printing document: " + e.toString());
			ret[1] += e.toString();
			try
			{
				if (out != null)
					out.close();
			}
			catch(Exception ee)
			{
			}
			return ret;
		}

		ArrayList<String> params = new ArrayList<String>();
		params.add(PROOT);
		params.add(LP);
		params.add("-d");
		params.add(printer);
		params.add("-n");
		params.add(String.valueOf(copies));
		params.add("-t");
		params.add(jobLabel);
		params.add("-o");
		params.add("media=" + mediaSize);
		if (landscape)
		{
			params.add("-o");
			params.add("landscape");
		}
		if (resolution != null)
		{
			params.add("-o");
			params.add("Resolution=" + resolution);
		}
		if (doubleSided.lpOption != null)
		{
			params.add("-o");
			params.add(doubleSided.lpOption);
		}
		if (pages != null)
		{
			params.add("-P");
			String pagesStr = "";
			for (PageRange r: pages)
			{
				if (pagesStr.length() > 0)
					pagesStr = pagesStr + ",";
				pagesStr += String.valueOf(r.getStart() + 1);
				if (r.getStart() != r.getEnd())
					pagesStr += "-" + String.valueOf(r.getEnd() + 1);
			}
			params.add(pagesStr);
		}
		// Chrome tends to pass bigger image than our paper size, making printer waste two pages, so we'll resize it to fit
		params.add("-o");
		params.add("fit-to-page");
		// if (job.getInfo().getAttributes().getMinMargins() != null) // Not supported yet
		params.add("/" + PIPE);

		Log.i(TAG, "Printing document command: " + Arrays.toString(params.toArray(new String[0])));
		Proc lp = new Proc(params.toArray(new String[0]), chrootPath(p));
		Log.i(TAG, "Printing document finished: status: " + lp.status + " msg: " + Arrays.toString(lp.out));
		pipeFile.delete();
		if (lp.status != 0) // There was an error
		{
			ret[0] = "";
			ret[1] += Arrays.toString(lp.out);
		}
		else if (lp.out.length > 0 && lp.out[0].startsWith("request id is "))
		{
			ret[0] = lp.out[0].substring("request id is ".length()).trim().split("\\s+")[0];
		}
		return ret;
	}

	synchronized public static Map<String, String> getPrinterModels(Context p)
	{
		final String modelsFileName = "printer-models.txt";
		File modelsFile = new File(chrootPath(p), modelsFileName);
		if (!modelsFile.exists() || modelsFile.length() < 100000)
		{
			Proc pp = new Proc(new String[] {PROOT, "/bin/sh", "-c", LPINFO + " -m > " + modelsFileName}, chrootPath(p));
		}
		TreeMap<String, String> models = new TreeMap<String, String>();
		try
		{
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(modelsFile)));
			String s;
			while ((s = in.readLine()) != null)
			{
				if (s.indexOf(" ") == -1)
					continue;
				models.put(s.substring(s.indexOf(" ") + 1), s.substring(0, s.indexOf(" ")));
			}
		}
		catch(Exception e)
		{
			Log.i(TAG, "Cannot read " + modelsFileName + ": " + e.toString());
		}
		return models;
	}

	synchronized public static void startCupsDaemon(Context p)
	{
		if (cupsd != null && isDaemonRunning(p))
			return;
		restartCupsDaemon(p);
	}

	synchronized public static void stopCupsDaemon(Context p)
	{
		if (cupsd == null)
			return;
		cupsd.destroy();
		dbus.destroy();
		cupsd = null;
	}

	synchronized public static void restartCupsDaemon(Context p)
	{
		if (cupsd != null)
		{
			cupsd.destroy();
			dbus.destroy();
		}
		cupsd = null;
		dbus = null;
		try
		{
			updateDns(p);
			dbus = Runtime.getRuntime().exec(new String[] {PROOT, DBUS, "--system"}, null, chrootPath(p));
			cupsd = Runtime.getRuntime().exec(new String[] {PROOT, CUPSD, "-f"}, null, chrootPath(p));
			for (int i = 0; i < 10 && !isDaemonRunning(p); i++)
			{
				try
				{
					Thread.sleep(200);
				}
				catch(InterruptedException e)
				{
				}
			}
		}
		catch(IOException e)
		{
		}
	}

	public static boolean isDaemonRunning(Context p)
	{
		Proc pp = new Proc(new String[] {PROOT, LPSTAT, "-r"}, chrootPath(p));
		if (pp.status != 0 || pp.out.length < 1 || pp.out[0].indexOf("scheduler is running") != 0)
			return false;
		return true;
	}

	synchronized public static void updateDns(Context p)
	{
		Proc pp = new Proc(new String[] {"./update-dns.sh"}, chrootPath(p));
	}

	public static String[] getNetworkTree(Context p, String login, String password, String domain)
	{
		if (login.length() > 0 && password.length() > 0)
		{
			File auth = null;
			try
			{
				auth = File.createTempFile("auth-", ".txt", chrootPath(p));
				auth.setReadable(false, false);
				auth.setReadable(true, true);
				auth.setWritable(false, false);
				auth.setWritable(true, true);
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(auth), "utf-8"));
				out.write("username = " + login + "\n");
				out.write("password = " + password + "\n");
				if (domain.length() > 0)
					out.write("domain = " + domain + "\n");
				out.close();
			}
			catch(Exception e)
			{
				return new Proc(new String[] {PROOT, "/usr/bin/smbtree", "-N", }, chrootPath(p)).out;
			}
			Proc ret = new Proc(new String[] {PROOT, "/usr/bin/smbtree", "-A", "/" + auth.getName()}, chrootPath(p));
			auth.delete();
			return ret.out;
		}
		updateDns(p);
		return new Proc(new String[] {PROOT, "/usr/bin/smbtree", "-N", }, chrootPath(p)).out;
	}

	public static int copyStream(InputStream stream, OutputStream out) throws java.io.IOException
	{
		byte[] buf = new byte[16384];
		int len = stream.read(buf);
		int totalLen = 0;
		while (len >= 0)
		{
			if(len > 0)
				out.write(buf, 0, len);
			totalLen += len;
			len = stream.read(buf);
		}
		stream.close();
		out.close();
		return totalLen;
	}

	private static final String TAG = "Cups";
}
