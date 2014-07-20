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
import android.text.util.Linkify;
import android.widget.ScrollView;
import java.util.*;
import android.text.TextWatcher;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.app.ProgressDialog;
import android.text.method.PasswordTransformationMethod;


public class AddPrinterActivity extends Activity
{
	private ScrollView scroll = null;
	private LinearLayout layout = null;
	private Button viewNetwork = null;
	private EditText name = null;
	private EditText server = null;
	private EditText printer = null;
	private EditText model = null;
	private Button selectModel = null;
	private EditText domain = null;
	private EditText user = null;
	private EditText password = null;
	private Button addPrinter = null;
	private ProgressDialog progressCircle = null;

	private Map<String, String> modelList = null;

	private String[] networkTree = null;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");

		scroll = new ScrollView(this);

		layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		layout.setPadding(10, 10, 10, 10);
		scroll.addView(layout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		viewNetwork = new Button(this);
		viewNetwork.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		viewNetwork.setText(getResources().getString(R.string.view_network_button));
		viewNetwork.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				final String[] networkTreeCopy = networkTree;
				AlertDialog.Builder builder = new AlertDialog.Builder(AddPrinterActivity.this);
				//builder.setTitle(R.string.network);
				builder.setItems(networkTreeCopy, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, final int which)
					{
						dialog.dismiss();
						String selected = networkTreeCopy[which];
						selected = selected.trim();
						if (!selected.startsWith("\\\\"))
							return;
						selected = selected.substring(2);
						if (selected.indexOf("\\") == -1)
							return;
						String server = selected.substring(0, selected.indexOf("\\"));
						String share = selected.substring(selected.indexOf("\\") + 1);
						share = share.split("\\s+")[0];
						AddPrinterActivity.this.server.setText(server);
						AddPrinterActivity.this.printer.setText(share);
						AddPrinterActivity.this.name.setText(server + "-" + share);
						String workgroup = "";
						for (int i = 0; i < which; i++)
						{
							if (networkTreeCopy[i].indexOf("\\") == -1)
								workgroup = networkTreeCopy[i];
						}
						if (workgroup.length() > 0)
							AddPrinterActivity.this.domain.setText(workgroup);
					}
				});
				builder.setNegativeButton(R.string.close, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface d, int s)
					{
						d.dismiss();
					}
				});
				builder.setPositiveButton(R.string.view_network_button_scan_again, new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface d, int s)
					{
						d.dismiss();
						updateNetworkTree();
					}
				});
				AlertDialog alert = builder.create();
				alert.setOwnerActivity(AddPrinterActivity.this);
				alert.show();
			}
		});
		layout.addView(viewNetwork);
		updateNetworkTree();

		TextView text = null;

		text = new TextView(this);
		text.setText(R.string.name_desc);
		text.setTextSize(20);
		layout.addView(text);

		name = new EditText(this);
		name.setHint(R.string.name_hint);
		layout.addView(name);

		text = new TextView(this);
		text.setText(R.string.server_desc);
		text.setTextSize(20);
		layout.addView(text);

		server = new EditText(this);
		server.setHint(R.string.server_hint);
		layout.addView(server);

		text = new TextView(this);
		text.setText(R.string.printer_desc);
		text.setTextSize(20);
		layout.addView(text);

		printer = new EditText(this);
		printer.setHint(R.string.printer_hint);
		layout.addView(printer);

		text = new TextView(this);
		text.setText(R.string.model_desc);
		text.setTextSize(20);
		layout.addView(text);

		model = new EditText(this);
		model.setHint(R.string.model_button_reading);
		model.setEnabled(false);
		model.addTextChangedListener(new TextWatcher()
		{
			public void afterTextChanged(Editable s)
			{
				if (s.length() >= 1 && modelList != null)
				{
					//selectModel.setEnabled(true);
					selectModel.setText(getResources().getString(R.string.model_button_search));
				}
				else
				{
					//selectModel.setEnabled(false);
					selectModel.setText(getResources().getString(R.string.model_button_type));
				}
			}
			public void beforeTextChanged(CharSequence s, int start, int count, int after)
			{
			}
			public void onTextChanged(CharSequence s, int start, int before, int count)
			{
			}
		});
		layout.addView(model);

		selectModel = new Button(this);
		selectModel.setText(getResources().getString(R.string.model_button_reading));
		selectModel.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				if (!model.isEnabled())
				{
					model.setEnabled(true);
					model.setText("");
					return;
				}
				String search = model.getText().toString().toLowerCase();
				final ArrayList<CharSequence> values = new ArrayList<CharSequence>();
				AlertDialog.Builder builder = new AlertDialog.Builder(AddPrinterActivity.this);
				builder.setTitle(R.string.select_printer_model);
				for (String s: modelList.keySet())
				{
					if (s.toLowerCase().indexOf(search) != -1)
					{
						Log.d(TAG, "Found model: " + s);
						values.add(s);
					}
				}
				if (values.size() == 0)
				{
					builder.setMessage(R.string.no_models_found);
					builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface d, int s)
						{
							d.dismiss();
						}
					});
				}
				else
				{
					builder.setItems(values.toArray(new CharSequence[0]), new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface dialog, int which)
						{
							model.setText(values.get(which));
							model.setEnabled(false);
							selectModel.setText(getResources().getString(R.string.model_button_clear));
						}
					});
				}
				AlertDialog alert = builder.create();
				alert.setOwnerActivity(AddPrinterActivity.this);
				alert.show();
			}
		});
		selectModel.setEnabled(false);
		layout.addView(selectModel);

		text = new TextView(this);
		text.setText(R.string.domain_desc);
		text.setTextSize(20);
		layout.addView(text);

		domain = new EditText(this);
		domain.setHint(R.string.domain_hint);
		layout.addView(domain);

		text = new TextView(this);
		text.setText(R.string.user_desc);
		text.setTextSize(20);
		layout.addView(text);

		user = new EditText(this);
		user.setHint(R.string.user_hint);
		layout.addView(user);

		text = new TextView(this);
		text.setText(R.string.password_desc);
		text.setTextSize(20);
		layout.addView(text);

		password = new EditText(this);
		password.setHint(R.string.password_hint);
		password.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
		password.setTransformationMethod(PasswordTransformationMethod.getInstance());
		layout.addView(password);

		addPrinter = new Button(this);
		addPrinter.setText(getResources().getString(R.string.add_printer_button));
		addPrinter.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				if (name.getText().length() == 0 || server.getText().length() == 0 ||
					printer.getText().length() == 0 || model.isEnabled() || model.getText().length() == 0)
				{
					AlertDialog.Builder builder = new AlertDialog.Builder(AddPrinterActivity.this);
					builder.setTitle(R.string.error);
					builder.setMessage(R.string.error_empty_fields);
					builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
					{
						public void onClick(DialogInterface d, int s)
						{
							d.dismiss();
						}
					});
					AlertDialog alert = builder.create();
					alert.setOwnerActivity(AddPrinterActivity.this);
					alert.show();
					return;
				}
				progressCircle.show();
				new Thread(new Runnable()
				{
					public void run()
					{
						Cups.addPrinter(AddPrinterActivity.this, name.getText().toString(), server.getText().toString(),
										printer.getText().toString(), modelList.get(model.getText().toString()),
										domain.getText().toString(), user.getText().toString(), password.getText().toString());
						progressCircle.dismiss();
						finish();
					}
				}).start();
			}
		});
		layout.addView(addPrinter);

		progressCircle = new ProgressDialog(this);

		setContentView(scroll);

		updateModelList();
	}

	private void updateModelList()
	{
		new Thread(new Runnable()
		{
			public void run()
			{
				modelList = Cups.getPrinterModels(AddPrinterActivity.this);
				runOnUiThread(new Runnable()
				{
					public void run()
					{
						selectModel.setText(getResources().getString(R.string.model_button_type));
						model.setHint(R.string.model_hint);
						model.setEnabled(true);
						selectModel.setEnabled(true);
					}
				});
			}
		}).start();
	}

	public void updateNetworkTree()
	{
		if (viewNetwork == null)
			return;
		viewNetwork.setEnabled(false);
		viewNetwork.setText(getResources().getString(R.string.view_network_button_scanning));
		new Thread(new Runnable()
		{
			public void run()
			{
				networkTree = Cups.getNetworkTree(AddPrinterActivity.this, user.getText().toString(), password.getText().toString(), domain.getText().toString());
				runOnUiThread(new Runnable()
				{
					public void run()
					{
						if (viewNetwork == null)
							return;
						viewNetwork.setEnabled(true);
						viewNetwork.setText(getResources().getString(R.string.view_network_button));
					}
				});
			}
		}).start();
	}

	static public final String TAG = "AddPrinterActivity";
}
