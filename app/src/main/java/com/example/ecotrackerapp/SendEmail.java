package com.example.ecotrackerapp;

import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;
import android.content.Context;

public class SendEmail {
    private final LogFile logfile;
    private static final String EMAIL_RECIPIENT="brian@earthviewsociety.org";

    private final Context context;
    SendEmail(Context context, LogFile file) {
        this.logfile = file;
        this.context = context;
        send();
    }

    private void send(){
        String auth = context.getPackageName() + ".fileprovider";
        Uri fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", logfile.getFile());

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("message/rfc822"); // or "text/plain", or use getMimeType
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{EMAIL_RECIPIENT});
        intent.putExtra(Intent.EXTRA_SUBJECT, "GPS Tracking Log");
        intent.putExtra(Intent.EXTRA_STREAM, fileUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(intent, "Send email via:"));

    }


}
