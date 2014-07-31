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
import java.util.concurrent.TimeUnit;
import android.content.pm.ActivityInfo;
import android.view.Display;
import android.text.InputType;
import android.util.Log;
import android.view.Surface;
import android.app.ProgressDialog;
import android.text.util.Linkify;
import android.provider.Settings;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.content.DialogInterface;
import android.net.Uri;
import java.util.*;


public class PrintJobs
{
	private static boolean destroyed = false;
	private static HashMap<android.printservice.PrintJob, String[]> trackedJobs = new HashMap<android.printservice.PrintJob, String[]>();
	private static CupsPrintService context = null;
	private static Handler mainThread = null;


	synchronized public static void init(final CupsPrintService c)
	{
		if (context != null)
			Log.e(TAG, "Error: Already initialized with context " + context + ", new context " + c);
		destroyed = false;
		context = c;
		mainThread = new Handler(context.getMainLooper());
		Log.d(TAG, "Creating jobs tracking thread");
		new Thread(new Runnable()
		{
			public void run()
			{
				trackJobsThread();
			}
		}).start();
	}

	synchronized public static void destroy()
	{
		Log.d(TAG, "Destroying jobs tracking thread");
		destroyed = true;
	}

	synchronized public static void trackJob(final android.printservice.PrintJob job)
	{
		trackedJobs.put(job, new String[] {job.getTag(), job.getInfo().getPrinterId().getLocalId()});
		Log.d(TAG, "Started tracking job: " + job.getTag() + " printer " + job.getInfo().getPrinterId().getLocalId());
	}

	synchronized public static void stopTrackingJob(final android.printservice.PrintJob job)
	{
		trackedJobs.remove(job);
	}

	private static String getJobStatus(final String[] jobInfo)
	{
		for (String s: jobInfo)
		{
			if (s.startsWith("Status:") && s.length() > "Status:".length() + 1)
				return s.substring("Status:".length() + 1);
		}
		return "";
	}

	private static boolean isJobSucceeded(final String[] jobInfo)
	{
		for (String s: jobInfo)
		{
			if (s.equals("Alerts: job-completed-successfully") || s.equals("Alerts: processing-to-stop-point"))
				return true;
		}
		return false;
	}

	synchronized private static HashMap<android.printservice.PrintJob, String[]> copyTrackedJobsArray()
	{
		// I'm to lazy to find out how to synchronize class object from static context, so I'll just create another synchronized method
		return new HashMap<android.printservice.PrintJob, String[]>(trackedJobs);
	}

	private static void trackJobsThread()
	{
		while (!destroyed)
		{
			try
			{
				Thread.sleep(10000);
			}
			catch (Exception e)
			{
			}
			// Invoking commandline tools is slow, so we will create local copy of trackedJobs, and only lock it when changing it
			HashMap<android.printservice.PrintJob, String[]> trackedCopy = copyTrackedJobsArray();
			final HashMap<String, Map<String, String[]> > activeJobs = new HashMap<String, Map<String, String[]> >();
			final HashMap<String, Map<String, String[]> > completedJobs = new HashMap<String, Map<String, String[]> >();
			for (final android.printservice.PrintJob job: trackedCopy.keySet())
			{
				final String jobName = trackedJobs.get(job)[0];
				final String printer = trackedJobs.get(job)[1];
				if (!activeJobs.containsKey(printer))
					activeJobs.put(printer, Cups.getPrintJobs(context, printer, false));
				if (activeJobs.get(printer).containsKey(jobName))
				{
					mainThread.post(new Runnable()
					{
						public void run()
						{
							String status = getJobStatus(activeJobs.get(printer).get(jobName));
							Log.d(TAG, "Print job " + jobName + " status: " + status);
							if (status.length() > 0)
								job.block(status);
							else if (!job.isStarted())
								job.start();
						}
					});
				}
				else
				{
					if (!completedJobs.containsKey(printer))
						completedJobs.put(printer, Cups.getPrintJobs(context, printer, true));
					mainThread.post(new Runnable()
					{
						public void run()
						{
							if (!completedJobs.get(printer).containsKey(jobName))
								job.fail(context.getResources().getString(R.string.error_job_disappeared));
							else if (isJobSucceeded(completedJobs.get(printer).get(jobName)))
								job.complete();
							else
							{
								String status = getJobStatus(completedJobs.get(printer).get(jobName));
								Log.d(TAG, "Print job " + jobName + " failed with status: " + status);
								job.fail(status);
							}
						}
					});
					stopTrackingJob(job);
				}
			}
		}
	}

	static public final String TAG = "PrintJobs";
}
