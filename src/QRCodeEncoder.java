/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.cups.android;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.result.AddressBookParsedResult;
import com.google.zxing.client.result.ParsedResult;
import com.google.zxing.client.result.ResultParser;
import com.google.zxing.common.BitMatrix;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * This class does the work of decoding the user's request and extracting all the data
 * to be encoded in a barcode.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
final class QRCodeEncoder {

  private static final int WHITE = 0xFFFFFFFF;
  private static final int BLACK = 0xFF000000;

  public static Bitmap encodeAsBitmap(String contents) {
    return encodeAsBitmap(contents, 200);
  }

  public static Bitmap encodeAsBitmap(String contents, int dimension) {
    try {
      return encodeAsBitmap(contents, dimension, BarcodeFormat.QR_CODE);
    } catch (WriterException e) { }
    return null;
  }

  public static Bitmap encodeAsBitmap(String contents, int dimension, BarcodeFormat format) throws WriterException {
    String contentsToEncode = contents;
    if (contentsToEncode == null) {
      return null;
    }
    Map<EncodeHintType,Object> hints = null;
    String encoding = guessAppropriateEncoding(contentsToEncode);
    if (encoding != null) {
      hints = new EnumMap<EncodeHintType,Object>(EncodeHintType.class);
      hints.put(EncodeHintType.CHARACTER_SET, encoding);
    }
    BitMatrix result;
    try {
      result = new MultiFormatWriter().encode(contentsToEncode, format, dimension, dimension, hints);
    } catch (IllegalArgumentException iae) {
      // Unsupported format
      return null;
    }
    int width = result.getWidth();
    int height = result.getHeight();
    int[] pixels = new int[width * height];
    for (int y = 0; y < height; y++) {
      int offset = y * width;
      for (int x = 0; x < width; x++) {
        pixels[offset + x] = result.get(x, y) ? BLACK : WHITE;
      }
    }

    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    return bitmap;
  }

  private static String guessAppropriateEncoding(CharSequence contents) {
    // Very crude at the moment
    for (int i = 0; i < contents.length(); i++) {
      if (contents.charAt(i) > 0xFF) {
        return "UTF-8";
      }
    }
    return null;
  }
}
