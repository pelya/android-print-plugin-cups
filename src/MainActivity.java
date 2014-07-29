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


public class MainActivity extends Activity
{
	private ScrollView scroll = null;
	private LinearLayout layout = null;
	private TextView text = null;
	private Button cancelJob = null;
	private Button reenablePrinters = null;
	private Button openSettings = null;
	private Button addPrinter = null;
	private Button deletePrinter = null;
	private Button viewNetwork = null;
	private String[] networkTree = null;
	private Button advancedInterface = null;
	private boolean destroyed = false;
	private boolean jobsThreadStarted = false;
	private String[] printJobs = new String[0];
	private Semaphore printJobsUpdate = new Semaphore(0);

	@Override protected void onCreate(Bundle savedInstanceState)
	{
		destroyed = false;
		jobsThreadStarted = false;
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		reinitUI();
		Installer.unpackData(this, text);
	}

	@Override synchronized protected void onDestroy()
	{
		Log.d(TAG, "onDestroy");
		destroyed = true;
		jobsThreadStarted = false;
		super.onDestroy();
	}

	@Override protected void onStart()
	{
		super.onStart();
		if (!Installer.unpacking)
			enableSettingsButton();
	}

	void reinitUI()
	{
		layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
		
		text = new TextView(this);
		//text.setMaxLines();
		text.setText(R.string.init);
		text.setText("Initializing");
		text.setTextSize(20);
		text.setPadding(20, 20, 20, 50);
		layout.addView(text);

		scroll = new ScrollView(this);
		scroll.addView(layout);

		setContentView(scroll);
	}

	void enableSettingsButton()
	{
		runOnUiThread(new Runnable()
		{
			public void run()
			{
				reinitUI();
				enableSettingsButtonPriv();
				if (!jobsThreadStarted)
				{
					updateActiveJobs();
					printJobsUpdate.release();
				}
				jobsThreadStarted = true;
			}
		});
	}

	private void enableSettingsButtonPriv()
	{
		text.setText(CupsPrintService.pluginEnabled ? R.string.service_running : R.string.enable_plugin);

		cancelJob = new Button(this);
		cancelJob.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		cancelJob.setText(R.string.cancel_job);
		cancelJob.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				if (printJobs == null)
					return;
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setTitle(R.string.cancel_job);
				builder.setItems(printJobs, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, final int which)
					{
						dialog.dismiss();
						if (printJobs.length > which) // Just in case jobs list changes while user is waiting
							Cups.cancelPrintJob(MainActivity.this, printJobs[which]);
						cancelJob.setEnabled(false);
						printJobsUpdate.release();
					}
				});
				builder.setPositiveButton(R.string.close, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface d, int s)
					{
						d.dismiss();
					}
				});
				AlertDialog alert = builder.create();
				alert.setOwnerActivity(MainActivity.this);
				alert.show();
			}
		});
		cancelJob.setEnabled(false);
		layout.addView(cancelJob);

		reenablePrinters = new Button(this);
		reenablePrinters.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		reenablePrinters.setText(R.string.restart_printing);
		reenablePrinters.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				reenablePrinters.setVisibility(View.GONE);
				new Thread(new Runnable()
				{
					public void run()
					{
						String[] printers = Cups.getPrinters(MainActivity.this);
						for (String p: printers)
						{
							Cups.enablePrinter(MainActivity.this, p);
						}
					}
				}).start();
			}
		});
		reenablePrinters.setVisibility(View.GONE);
		layout.addView(reenablePrinters);

		openSettings = new Button(this);
		openSettings.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		openSettings.setText(getResources().getString(CupsPrintService.pluginEnabled ? R.string.settings_button : R.string.enable_plugin_button));
		openSettings.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				MainActivity.this.startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS));
			}
		});
		layout.addView(openSettings);

		addPrinter = new Button(this);
		addPrinter.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		addPrinter.setText(getResources().getString(R.string.add_printer_button));
		addPrinter.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				MainActivity.this.startActivity(new Intent(MainActivity.this, AddPrinterActivity.class));
			}
		});
		layout.addView(addPrinter);

		deletePrinter = new Button(this);
		deletePrinter.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		deletePrinter.setText(getResources().getString(R.string.delete_printer_button));
		deletePrinter.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				final String[] printers = Cups.getPrinters(MainActivity.this);
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setTitle(R.string.delete_printer_button);
				builder.setItems(printers, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, final int which)
					{
						dialog.dismiss();
						AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
						builder.setTitle(getResources().getString(R.string.delete_printer_button) + " " + printers[which]);
						builder.setPositiveButton(R.string.delete_printer_button, new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface d, int s)
							{
								Cups.deletePrinter(MainActivity.this, printers[which]);
								d.dismiss();
							}
						});
						builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface d, int s)
							{
								d.dismiss();
							}
						});
						AlertDialog alert = builder.create();
						alert.setOwnerActivity(MainActivity.this);
						alert.show();
					}
				});
				builder.setPositiveButton(R.string.cancel, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface d, int s)
					{
						d.dismiss();
					}
				});
				AlertDialog alert = builder.create();
				alert.setOwnerActivity(MainActivity.this);
				alert.show();
			}
		});
		layout.addView(deletePrinter);

		viewNetwork = new Button(this);
		viewNetwork.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		viewNetwork.setText(getResources().getString(R.string.view_network_button));
		viewNetwork.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				String[] networkTreeCopy = networkTree;
				TextView text = new TextView(MainActivity.this);
				String s = "No network shares found";
				if (networkTreeCopy != null && networkTreeCopy.length > 0)
				{
					s = "";
					for (String ss: networkTreeCopy)
						s = s + ss + "\n";
				}
				text.setText(s);
				text.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				text.setPadding(0, 5, 0, 5);
				text.setTextSize(20.0f);
				text.setGravity(Gravity.LEFT);
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				ScrollView scroll = new ScrollView(MainActivity.this);
				scroll.addView(text, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
				Button ok = new Button(MainActivity.this);
				final AlertDialog alertDismiss[] = new AlertDialog[1];
				ok.setOnClickListener(new View.OnClickListener()
				{
					public void onClick(View v)
					{
						alertDismiss[0].dismiss();
					}
				});
				ok.setText(R.string.ok);
				Button scanAgain = new Button(MainActivity.this);
				scanAgain.setOnClickListener(new View.OnClickListener()
				{
					public void onClick(View v)
					{
						alertDismiss[0].cancel();
						updateNetworkTree();
					}
				});
				scanAgain.setText(R.string.view_network_button_scan_again);
				LinearLayout layout = new LinearLayout(MainActivity.this);
				layout.setOrientation(LinearLayout.VERTICAL);
				layout.addView(scroll);
				layout.addView(ok);
				layout.addView(scanAgain);
				builder.setView(layout);
				AlertDialog alert = builder.create();
				alertDismiss[0] = alert;
				alert.setOwnerActivity(MainActivity.this);
				alert.show();
			}
		});
		layout.addView(viewNetwork);
		updateNetworkTree();

		advancedInterface = new Button(this);
		advancedInterface.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		advancedInterface.setText(getResources().getString(R.string.advanced_interface_button));
		advancedInterface.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://127.0.0.1:6631/")));
			}
		});
		layout.addView(advancedInterface);
	}

	private void updateNetworkTree()
	{
		viewNetwork.setEnabled(false);
		viewNetwork.setText(getResources().getString(R.string.view_network_button_scanning));
		new Thread(new Runnable()
		{
			public void run()
			{
				networkTree = Cups.getNetworkTree(MainActivity.this, "", "", "");
				synchronized(MainActivity.this)
				{
					if (destroyed)
						return;
					runOnUiThread(new Runnable()
					{
						public void run()
						{
							if (viewNetwork == null || destroyed)
								return;
							viewNetwork.setEnabled(true);
							viewNetwork.setText(getResources().getString(R.string.view_network_button));
						}
					});
				};
			}
		}).start();
	}

	private void updateActiveJobs()
	{
		new Thread(new Runnable()
		{
			public void run()
			{
				try
				{
					printJobsUpdate.tryAcquire(4, TimeUnit.SECONDS);
				}
				catch (Exception e)
				{
				}
				final StringBuffer str = new StringBuffer();
				String[] printers = Cups.getPrinters(MainActivity.this);
				final ArrayList<String> jobList = new ArrayList<String>();
				for (String p: printers)
				{
					Map<String, String[]> jobs = Cups.getPrintJobs(MainActivity.this, p);
					for (String j: jobs.keySet())
					{
						str.append(j + ":\n");
						jobList.add(j);
						for (String stat: jobs.get(j))
							str.append(stat + "\n");
					}
				}
				Log.d(TAG, "updateActiveJobs: updating UI");
				synchronized(MainActivity.this)
				{
					if (destroyed)
						return;
					runOnUiThread(new Runnable()
					{
						public void run()
						{
							if (destroyed)
								return;
							printJobs = jobList.toArray(new String[0]);
							if (str.toString().length() == 0)
							{
								cancelJob.setEnabled(false);
								text.setText(R.string.no_active_jobs);
								reenablePrinters.setVisibility(View.GONE);
							}
							else
							{
								text.setText(str.toString());
								cancelJob.setEnabled(true);
								if (str.toString().contains("Alerts: printer-stopped"))
									reenablePrinters.setVisibility(View.VISIBLE);
								else
									reenablePrinters.setVisibility(View.GONE);
							}
							updateActiveJobs();
						}
					});
				}
			}
		}).start();
	}

	static public final String TAG = "MainActivity";
}
