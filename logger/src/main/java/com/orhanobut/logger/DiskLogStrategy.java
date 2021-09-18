package com.orhanobut.logger;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.orhanobut.logger.Utils.checkNotNull;

/**
 * Abstract class that takes care of background threading the file log operation on Android.
 * implementing classes are free to directly perform I/O operations there.
 *
 * Writes all logs to the disk with default CSV format.
 */
public class DiskLogStrategy implements LogStrategy {

  @NonNull private final Handler handler;

  public DiskLogStrategy(@NonNull Handler handler) {
    this.handler = checkNotNull(handler);
  }

  @Override public void log(int level, @Nullable String tag, @NonNull String message) {
    checkNotNull(message);

    // do nothing on the calling thread, simply pass the tag/msg to the background thread
    handler.sendMessage(handler.obtainMessage(level, message));
  }

  static class WriteHandler extends Handler {

    @NonNull private final File folder;
    private final int maxFileSize;

    WriteHandler(@NonNull Looper looper, @NonNull File folder, int maxFileSize) {
      super(checkNotNull(looper));
      this.folder = checkNotNull(folder);
      this.maxFileSize = maxFileSize;
    }

    @Override
    public void handleMessage(@NonNull Message msg) {
      String content = (String) msg.obj;

      BufferedOutputStream fileWriter = null;

      try {
        File logFile = getLogFile(folder, "logs");
        fileWriter = new BufferedOutputStream(new FileOutputStream(logFile, true));

        writeLog(fileWriter, content);

        fileWriter.flush();
      } catch (IOException e) {
        if (fileWriter != null) {
          try {
            fileWriter.flush();
            fileWriter.close();
          } catch (IOException e1) {
            System.err.println("Error writing DiskLogStrategy");
            System.err.println(e1);
          }
        }
      } finally {
        try {
          if (fileWriter != null)
            fileWriter.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    /**
     * This is always called on a single background thread.
     * Implementing classes must ONLY write to the fileWriter and nothing more.
     * The abstract class takes care of everything else including close the stream and catching IOException
     *
     * @param fileWriter an instance of FileWriter already initialised to the correct file
     */
    private void writeLog(@NonNull BufferedOutputStream fileWriter, @NonNull String content) throws IOException {
      checkNotNull(fileWriter);
      checkNotNull(content);
      // Default charset encoding
      fileWriter.write(content.getBytes());
    }

    private File getLogFile(@NonNull File folderName, @NonNull String fileName) throws IOException {
      checkNotNull(folderName);
      checkNotNull(fileName);

      File folderFile = folderName;
      if (!folderFile.exists()) {
        // Create and check if folder is correct created
        if (!folderFile.mkdirs()) {
          throw new IOException(String.format("Folder %s not created !", folderName));
        }
      }

      int newFileCount = 0;
      File newFile;
      File existingFile = null;

      newFile = new File(folderFile, String.format("%s_%s.csv", fileName, newFileCount));
      while (newFile.exists()) {
        existingFile = newFile;
        newFileCount++;
        newFile = new File(folderFile, String.format("%s_%s.csv", fileName, newFileCount));
      }

      if (existingFile != null) {
        if (existingFile.length() >= maxFileSize) {
          return newFile;
        }
        return existingFile;
      }

      return newFile;
    }
  }
}
