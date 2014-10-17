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
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
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
import android.widget.Toast;
import android.net.Uri;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.print.*;
import android.print.pdf.PrintedPdfDocument;
import android.graphics.pdf.PdfDocument;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.StaticLayout;
import android.text.Layout;
import android.text.TextPaint;

public class SharePrinterActivity extends Activity
{
	private ScrollView scroll = null;
	private LinearLayout layout = null;
	private EditText notes = null;
	private EditText user = null;
	private EditText password = null;
	private Button shareClipboard = null;
	private Button printQr = null;
	private Button close = null;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		final String name = getIntent().getStringExtra("n");
		if (name == null)
			finish();

		scroll = new ScrollView(this);
		setContentView(scroll);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

		layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		layout.setPadding(10, 10, 10, 10);
		scroll.addView(layout, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		TextView text = null;

		text = new TextView(this);
		text.setText(getResources().getString(R.string.share_printer_x_address, name));
		text.setTextSize(20);
		text.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
		text.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		layout.addView(text);

		text = new TextView(this);
		text.setText(R.string.user_desc_optional);
		text.setTextSize(20);
		layout.addView(text);

		user = new EditText(this);
		user.setHint(R.string.user_hint);
		user.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		layout.addView(user);

		text = new TextView(this);
		text.setText(R.string.password_desc_optional);
		text.setTextSize(20);
		layout.addView(text);

		password = new EditText(this);
		password.setHint(R.string.password_hint);
		password.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
		layout.addView(password);

		text = new TextView(this);
		text.setText(R.string.notes_desc);
		text.setTextSize(20);
		layout.addView(text);

		notes = new EditText(this);
		notes.setHint(R.string.notes_hint);
		notes.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
		notes.setMinLines(2);
		notes.setMaxLines(5);
		layout.addView(notes);

		shareClipboard = new Button(this);
		shareClipboard.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		shareClipboard.setText(getResources().getString(R.string.share_to_clipboard));
		shareClipboard.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				final Uri uriNoAuth = Cups.getPrinterAddress(SharePrinterActivity.this, name);
				if (uriNoAuth == null)
					return;
				showPasswordWarning(new Runnable()
				{
					public void run()
					{
						Log.d(TAG, "Printer URI: " + uriNoAuth.toString());
						Uri.Builder uri = uriNoAuth.buildUpon();
						if (user.getText().toString().length() > 0)
							uri.appendQueryParameter("u", user.getText().toString());
						if (password.getText().toString().length() > 0)
							uri.appendQueryParameter("pw", password.getText().toString());

						ClipData clip = ClipData.newUri(getContentResolver(), name, uri.build());
						ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
						clipboard.setPrimaryClip(clip);
						Toast.makeText(SharePrinterActivity.this, R.string.copied_to_clipboard, Toast.LENGTH_LONG).show();
					}
				});
			}
		});
		layout.addView(shareClipboard);

		printQr = new Button(this);
		printQr.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		printQr.setText(getResources().getString(R.string.print_qr));
		printQr.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				final Uri uriNoAuth = Cups.getPrinterAddress(SharePrinterActivity.this, name);
				if (uriNoAuth == null)
					return;
				showPasswordWarning(new Runnable()
				{
					public void run()
					{
						Log.d(TAG, "Printer URI: " + uriNoAuth.toString());
						Uri.Builder uri = uriNoAuth.buildUpon();
						if (user.getText().toString().length() > 0)
							uri.appendQueryParameter("u", user.getText().toString());
						if (password.getText().toString().length() > 0)
							uri.appendQueryParameter("pw", password.getText().toString());

						Bitmap qr = QRCodeEncoder.encodeAsBitmap("URI:" + uri.build().toString());
						Bitmap myAppAddr = QRCodeEncoder.encodeAsBitmap("URI:" + getResources().getString(R.string.google_play_url));
						PrintManager printManager = (PrintManager)getSystemService(Context.PRINT_SERVICE);
						printManager.print(getResources().getString(R.string.share_printer_address) + " " + name,
											new PrintQrCode(name, qr, myAppAddr, notes.getText().toString()),
											new PrintAttributes.Builder()
												.setMediaSize(PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
												.setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME).build());
					}
				});
			}
		});
		layout.addView(printQr);

		text = new TextView(this);
		text.setText("");
		layout.addView(text);

		close = new Button(this);
		close.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		close.setText(getResources().getString(R.string.close));
		close.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				finish();
			}
		});
		layout.addView(close);
	}

	void showPasswordWarning(final Runnable r)
	{
		if (password.getText().toString().length() == 0)
		{
			runOnUiThread(r);
			return;
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.share_password_warning_title);
		builder.setMessage(R.string.share_password_warning);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface d, int s)
			{
				d.dismiss();
				runOnUiThread(r);
			}
		});
		builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface d, int s)
			{
				password.setText("");
				d.dismiss();
				runOnUiThread(r);
			}
		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener()
		{
			public void onCancel(DialogInterface dialog)
			{
				password.setText("");
				runOnUiThread(r);
			}
		});
		AlertDialog alert = builder.create();
		alert.setOwnerActivity(this);
		alert.show();
	}

	class PrintQrCode extends PrintDocumentAdapter
	{
		String name;
		Bitmap qr;
		Bitmap myAppAddr;
		String notes;
		PrintedPdfDocument pdf;

		public PrintQrCode(String name, Bitmap qr, Bitmap myAppAddr, String notes)
		{
			this.name = name;
			this.qr = qr;
			this.myAppAddr = myAppAddr;
			this.notes = notes;
		}

		@Override
		public void onLayout(	PrintAttributes oldAttributes,
								PrintAttributes newAttributes,
								CancellationSignal cancellationSignal,
								LayoutResultCallback callback,
								Bundle metadata)
		{
			Log.d(TAG, "Creating new PDF");
			pdf = new PrintedPdfDocument(SharePrinterActivity.this, newAttributes);
			if (cancellationSignal.isCanceled())
			{
				callback.onLayoutCancelled();
				return;
			}
			PrintDocumentInfo info = new PrintDocumentInfo
				.Builder("printer_qr_code.pdf")
				.setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
				.setPageCount(1)
				.build();
			callback.onLayoutFinished(info, true);
		}

		@Override
		public void onWrite(final PageRange[] pageRanges,
							final ParcelFileDescriptor destination,
							final CancellationSignal cancellationSignal,
							final WriteResultCallback callback)
		{
			if (pageRanges.length == 0 || !(
				pageRanges[0].getStart() <= 0 && pageRanges[0].getEnd() >= 0 ||
				pageRanges[0] == PageRange.ALL_PAGES))
			{
				Log.d(TAG, "Saving PDF failed - no valid page range");
				return;
			}

			if (cancellationSignal.isCanceled())
			{
				callback.onWriteCancelled();
				pdf.close();
				pdf = null;
				return;
			}

			PdfDocument.Page page = pdf.startPage(0);
			drawPage(page);
			pdf.finishPage(page);

			Log.d(TAG, "Saving PDF");
			try
			{
				pdf.writeTo(new FileOutputStream(destination.getFileDescriptor()));
				Log.w(TAG, "Saving PDF succeeded");
			} catch (IOException e) {
				Log.w(TAG, "Saving PDF failed: " + e.toString());
				callback.onWriteFailed(e.toString());
				return;
			} finally {
				pdf.close();
				pdf = null;
			}
			callback.onWriteFinished(new PageRange[]{ new PageRange(0, 0) });
		}

		private void drawPage(PdfDocument.Page page)
		{
			Log.d(TAG, "Drawing PDF page");
			Canvas canvas = page.getCanvas();
			int w = canvas.getWidth();
			int h = canvas.getHeight();
			int x = w / 2;
			int y = 0;
			int size = h / 50;
			Log.d(TAG, "w " + w + " h " + h + " x " + x + " y " + y + " size " + size + " bounds " + canvas.getClipBounds().toString());
			//layout.draw(canvas);
			Paint paint = new Paint();
			paint.setColor(Color.BLACK);
			paint.setTextSize(size * 1.5f);
			paint.setTextAlign(Paint.Align.CENTER);

			y += (paint.descent() - paint.ascent()) * 2.5;
			canvas.drawText(getResources().getString(R.string.add_printer_android, name), x, y, paint);
			paint.setTextSize(size / 1.3f);
			y += (paint.descent() - paint.ascent()) * 1.5;
			canvas.drawText(getResources().getString(R.string.add_printer_android_ver), x, y, paint);
			y += (paint.descent() - paint.ascent()) * 1.2;
			canvas.drawText(getResources().getString(R.string.install_barcode_scanner, name), x, y, paint);
			paint.setTextSize(size);
			y += (paint.descent() - paint.ascent()) * 1.5;
			canvas.drawText(getResources().getString(R.string.install_printer_plugin, getResources().getString(R.string.app_name)), x, y, paint);
			y += (paint.descent() - paint.ascent()) * 0.2;

			int qrSize = size * 13;
			paint.setFilterBitmap(false);
			canvas.drawBitmap(myAppAddr, null, new Rect(x - qrSize / 2, y, x + qrSize / 2, y + qrSize), paint);
			y += qrSize;

			y += (paint.descent() - paint.ascent()) * 0.7;
			canvas.drawText(getResources().getString(R.string.install_printer_plugin_enable, getResources().getString(R.string.app_name)), x, y, paint);
			y += (paint.descent() - paint.ascent()) * 1;
			canvas.drawText(getResources().getString(R.string.scan_qr, name), x, y, paint);
			paint.setTextSize(size / 1.3f);
			y += (paint.descent() - paint.ascent()) * 1;
			canvas.drawText(getResources().getString(R.string.scan_qr_open_browser), x, y, paint);
			paint.setTextSize(size);
			y += (paint.descent() - paint.ascent()) * 0.2;

			qrSize = size * 20;
			canvas.drawBitmap(qr, null, new Rect(x - qrSize / 2, y, x + qrSize / 2, y + qrSize), paint);
			y += qrSize;

			y += (paint.descent() - paint.ascent()) * 0.8;
			for (String line: notes.split("\n"))
			{
				canvas.drawText(line, x, y, paint);
				y += (paint.descent() - paint.ascent()) * 1;
			}
		}
	}

	static public final String TAG = "SharePrinterActivity";
}
