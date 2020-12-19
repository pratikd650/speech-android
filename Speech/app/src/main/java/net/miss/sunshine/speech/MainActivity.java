package net.miss.sunshine.speech;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.text.style.BackgroundColorSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.text.*;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;


public class MainActivity extends AppCompatActivity implements View.OnClickListener, CompoundButton.OnCheckedChangeListener {

    GoogleSignInClient mGoogleSignInClient;
    private OkHttpClient client;
    private String idToken;
    private WebSocket webSocket;
    boolean signedIn = false;
    boolean socketOpened = false;
    boolean recordingStarted = false;
    private Map<String, Map<String, JSONObject>> results = new LinkedHashMap<>();
    private List<String> resultItems = new ArrayList<>();
    private RecyclerView recyclerView;
    MyRecyclerViewAdapter viewAdapter;
    Toolbar myToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set up the toolbar  https://developer.android.com/training/appbar/setting-up
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);


        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                //.requestIdToken("480181438061-ffjaa4r279af8rlr190in5fm40qv7dro.apps.googleusercontent.com")
                //.requestIdToken("480181438061-hthmagt8t2cn56l8d08ek4e33njo9h5k.apps.googleusercontent.com")
                //.requestIdToken("480181438061-3ctue79ce74eqq24naajad2793lin88f.apps.googleusercontent.com")
                .requestIdToken("480181438061-hs781145qtaelkqmpvopfl68ovfuinsc.apps.googleusercontent.com")
                .requestEmail()
                .build();

        // Build a GoogleSignInClient with the options specified by gso.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.sign_in_button).setOnClickListener(this);
        //findViewById(R.id.action_record).setOnClickListener(this);
        ((Switch)findViewById(R.id.action_record)).setOnCheckedChangeListener(this);
        findViewById(R.id.action_record).setVisibility(View.GONE);
        recyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        // use this setting to improve performance if you know that changes
        // in content do not change the layout size of the RecyclerView
        recyclerView.setHasFixedSize(true);

        // use a linear layout manager
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        // specify an adapter (see also next example)
        viewAdapter = new MyRecyclerViewAdapter();
        recyclerView.setAdapter(viewAdapter);

        // https://medium.com/@ssaurel/learn-to-use-websockets-on-android-with-okhttp-ba5f00aea988
        // Need to send a ping message every 5 seconds to prevent timeout
        client = new OkHttpClient.Builder()
                .pingInterval(5, TimeUnit.SECONDS)
                .build();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // disable the logout menu depending on signed in state
        menu.findItem(R.id.action_logout).setEnabled(signedIn);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_logout:
                signOut();
                return true;
            default:
                // If we got here, the user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class MyViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView textView;
        public MyViewHolder(TextView v) {
            super(v);
            textView = v;
        }
    }
    class MyRecyclerViewAdapter extends RecyclerView.Adapter<MyViewHolder> {

        // Create new views (invoked by the layout manager)
        @NonNull
        @Override
        public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // create a new view
            TextView textview = new TextView(MainActivity.this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                textview.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
            }
            textview.setTextSize(40);
            MyViewHolder vh = new MyViewHolder(textview);
            return vh;
        }

        // Replace the contents of a view (invoked by the layout manager)
        @Override
        public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {

            String key = resultItems.get(position);
            SpannableStringBuilder sb = new SpannableStringBuilder();
            try {
                for(JSONObject result : results.get(key).values()) {
                    JSONObject alt = result.getJSONArray("alternatives").getJSONObject(0);
                    String transcript = alt.getString("transcript");
                    int pos = sb.length();
                    sb.append(transcript);
                    // Use spans to color the nonFinal text as green
                    // https://medium.com/androiddevelopers/underspanding-spans-1b91008b97e4
                    // https://itnext.io/android-official-spans-all-in-one-6d23167b1bb9
                    if (!result.getBoolean("isFinal")) {
                        sb.setSpan(new BackgroundColorSpan(Color.parseColor("#2ABA8F")), pos, sb.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            holder.textView.setText(sb);
        }

        // Return the size of your dataset (invoked by the layout manager)
        @Override
        public int getItemCount() {
            //return 50;
            return resultItems.size();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Don't check for alreadySignedIn, because the openId token will be expired
        /*
        // Check for existing Google Sign In account, if the user is already signed in
        // the GoogleSignInAccount will be non-null.
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        if (account != null) {
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            System.out.println("Already Signed in " + account.getEmail());
            idToken = account.getIdToken();
            start();
        } else {
            findViewById(R.id.logout_button).setVisibility(View.GONE);
        }

         */
    }

    private void setResult(String clientId, String streamId, JSONObject result, boolean isFinal) throws JSONException {
        String key = clientId + "_" + streamId;
        final boolean added;
        if (!results.containsKey(key)) {
            results.put(key, new LinkedHashMap<String, JSONObject>());
            resultItems.add(key);
            added = true;
        } else {
            added = false;
        }
        final int pos = resultItems.indexOf(key);
        results.get(key).put(result.getString("startTime"), result);
        recyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (added) {
                    viewAdapter.notifyItemInserted(pos);
                } else {
                    viewAdapter.notifyItemChanged(pos);
                }
            }
        });


        /*
        StringBuilder builder = new StringBuilder();
        SpannableStringBuilder sbuilder = new SpannableStringBuilder();
        for(Map<String, JSONObject> conv : results.values()) {
            builder.append("<p>\n");
            for(JSONObject r : conv.values()) {
                JSONObject alt = r.getJSONArray("alternatives").getJSONObject(0);
                String transcript = alt.getString("transcript");
                SpannableString spanned = new SpannableString(transcript);

                boolean f = r.getBoolean("isFinal");
                if (!f) {

                    builder.append("<i>");
                    builder.append(Html.escapeHtml(transcript));
                    builder.append("</i>");
                } else {
                    builder.append(transcript);
                }
            }
            builder.append("</p>\n");
        }
         */

    }
    private void start() {
        // See https://medium.com/@ssaurel/learn-to-use-websockets-on-android-with-okhttp-ba5f00aea988
        final Request request = new Request.Builder().url("wss://little-miss-sunshine.net/audioBlob?id_token="+idToken).build();
        //Request request = new Request.Builder().url("wss://echo.websocket.org").build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NotNull WebSocket webSocket1, @NotNull Response response) {
                webSocket = webSocket1;
                System.out.println("Web socket opened");
                socketOpened = true;
            }
            @Override
            public void onMessage(WebSocket webSocket, String text) {
                System.out.println("got message " + text);
                try {
                    JSONObject cmd = new JSONObject(text);
                    if (cmd.getString("oper").equals("result")) {
                        String clientId = cmd.getString("clientId");
                        String streamId = cmd.getString("streamId");
                        JSONObject result = cmd.getJSONObject("result");
                        boolean isFinal = result.getBoolean("isFinal");
                        setResult(clientId, streamId, result, isFinal);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                t.printStackTrace();
                System.err.println("Failed to open websocket:");
            }

            @Override
            public void onClosing(@NotNull WebSocket webSocket1, int code, @NotNull String reason) {
                System.out.println("Closing websocket: " + reason);
                final int NORMAL_CLOSURE_STATUS = 1000;
                webSocket1.close(NORMAL_CLOSURE_STATUS, null);
                socketOpened = false;
            }


        });


        //client.dispatcher().executorService().shutdown();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sign_in_button:
                signIn();
                break;

            // ...
        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.action_record:
                if (isChecked && !recordingStarted) {
                    checkPermissionAndStartRecord();
                }
                if (!isChecked && recordingStarted){
                    stopRecord();
                }
        }
    }

    private static final int RC_SIGN_IN = 200;

    private void signIn() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    private void signOut() {
        if (socketOpened) {
            webSocket.close(1000, null);
        }

        mGoogleSignInClient.signOut()
            .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
                    findViewById(R.id.action_record).setVisibility(View.GONE);
                    signedIn = false;
                }
            });
    }

    // Requesting permission to RECORD_AUDIO
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    AudioRecord audioRecord = null;
    int bufferSizeInShorts;
    int streamId = 0;
    short buffer[];
    boolean isSpeaking = false;


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }

        if (permissionToRecordAccepted) {
          startRecord();
        }

    }

    /**
     * check if we have permission to record audio, otherwise request for it
     */
    private boolean checkOrRequestRecordAudioPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED) {
                // put your code for Version>=Marshmallow
                return true;
            } else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                    Toast.makeText(this,
                            "SpeechToText needs access to audio", Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
                return false;
            }

        } else {
            // put your code for Version < Marshmallow
            return true;
        }
    }

    private void checkPermissionAndStartRecord() {
        // if we don't have permission to record, request for it, and on the onRequestPermissionsResult, do the startRecord
        if (audioRecord == null && !checkOrRequestRecordAudioPermission()) {
            return;
        }
        startRecord();
    }

    // These thresholds are from vadlite
    // https://bitbucket.org/Jojo29/vadlite/src/master/VADLite/app/src/main/java/ethz/ch/vadlite/ConfigVAD.java
    public static final double DEVICE_NOISE_LEVEL = 0.01; //the level of noise by device
    public static final double RMS_THRESHOLD = 0.012; //If rms is above this threshold, then the sample is no silence

    /**
     * Estimate the RMS of the sound sample
     * @param buffer
     * @return
     */
    static double calculateRMS(short[] buffer, int length) {
        double energy = 0;

        for (short sample: buffer){
            // the sample is in the range -32768 to 32767, scale it  to -1 to +1
            double mappedSample = (double) sample/ Math.abs(Short.MAX_VALUE);

            if(Math.abs(mappedSample) > DEVICE_NOISE_LEVEL){
                energy+=mappedSample*mappedSample;
            }
        }

        double rms = length == 0 ? 0 : Math.sqrt(energy/length);
        return rms;
    }

    static final int sampleRateInHz = 16000;
    LinkedList<short[]> pendingBuffers = new LinkedList<>();
    int pendingSize = 0;

    int speechEndSize = 0; // contiguous samples of low rms , when isSpeaking=true
    int speechStartSize = 0; // contiguous samples of high rms, when isSpeaking=false

    final double speechStartThreshold = 0.25; // 0.25 seconds of high rms will start sending speech to backend
    final double speechEndThreshold = 1; // 1 second of low rms will stop sending speech to backend
    final double pendingSpeechThreshold = 1.5; // 1.5 seconds of pending speech will be sent

    private void startSpeech() {
        streamId++;
        try {
            JSONObject start = new JSONObject();
            start.put("oper", "start");
            start.put("streamId", streamId);
            start.put("provider", "gcloud");
            webSocket.send(start.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // send the pending buffer
        ByteBuffer buffer1 = ByteBuffer.allocate(pendingSize * 2);
        buffer1.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer buffer2 = buffer1.asShortBuffer();
        while(pendingBuffers.size() > 0) {
            short[] buf2 = pendingBuffers.remove();
            buffer2.put(buf2, 0, buf2.length);
            double rms2 = calculateRMS(buf2, buf2.length);
            System.out.println("adding pending buf size=" + buf2.length + " rms2=" + rms2);
        }
        pendingSize = 0;
        ByteString bytes  = ByteString.of(buffer1.array());
        webSocket.send(bytes);
        System.out.println("Sending pending bytes to websocket " + bytes.size());
    }

    private void stopSpeech() {
        try {
            JSONObject start = new JSONObject();
            start.put("oper", "end");
            start.put("streamId", streamId);
            webSocket.send(start.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    private void processAudio(short[] buffer, int size) {
        double rms = calculateRMS(buffer, size);
        System.out.println("Read bytes from microphone size=" + size + " rms=" + rms);

        if (!socketOpened)
            return;

        // is the rms low? we might have stopped speaking
        if (rms < RMS_THRESHOLD) {
            speechStartSize = 0; // reset speech start counter
            if (isSpeaking) {
                speechEndSize += size;
                // do we have 1 second of silence, then stop sending to backend
                if (speechEndSize >= sampleRateInHz * speechEndThreshold) {
                    System.out.println("Ending speech");
                    isSpeaking = false;
                    speechEndSize = 0;
                    stopSpeech();
                }
            }
        }
        // is the rms high, we might have started speaking
        else {
            speechEndSize = 0; // reset speechEndSize counter
            if (!isSpeaking) {
                speechStartSize += size;
                System.out.println("Adding to speechStart " + speechStartSize);
                if (speechStartSize > sampleRateInHz * speechStartThreshold) {
                    isSpeaking = true;
                    startSpeech();
                }
            }
        }

        if (isSpeaking) {
            // need to convert the buffer to little endian
            ByteBuffer buffer1 = ByteBuffer.allocate(size * 2);
            buffer1.order(ByteOrder.LITTLE_ENDIAN);
            buffer1.asShortBuffer().put(buffer, 0, size);
            ByteString bytes = ByteString.of(buffer1.array());
            webSocket.send(bytes);
            System.out.println("Sending bytes to websocket " + bytes.size());
        } else {
            // add to pending buffer, these are not sent to backend
            short[] buf2 = new short[size];
            System.arraycopy(buffer, 0, buf2, 0, size);
            pendingBuffers.add(buf2);
            pendingSize += size;
            double rms2 = calculateRMS(buf2, size);
            // if we have crossed 1 second of pending, then remove old buffers
            while(pendingSize > sampleRateInHz * pendingSpeechThreshold) {
                buf2 = pendingBuffers.remove();
                pendingSize -= buf2.length;
            }
            System.out.println("Adding to pending. pendingSize=" + pendingSize + " rms2=" + rms2);
        }

    }
    private void startRecord() {
        if (audioRecord == null) {
            int audioSource = MediaRecorder.AudioSource.MIC;

            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int channelConfig = AudioFormat.CHANNEL_IN_MONO;
            bufferSizeInShorts = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat) / 2;
            //bufferSizeInShorts = 2048; // Use 2048 because that's what the javascript uses
            audioRecord = new AudioRecord(audioSource, sampleRateInHz,
                    channelConfig,
                    audioFormat, bufferSizeInShorts * 2);
            buffer = new short[bufferSizeInShorts];
        }


        audioRecord.startRecording();
        recordingStarted = true;
        System.out.println("Started recording streamId=" + streamId);
        new Thread(new Runnable() {
            @Override
            public void run() {

                while(audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING && recordingStarted) {
                    int size = audioRecord.read(buffer, 0, bufferSizeInShorts);
                    processAudio(buffer, size);
                }
            }
        }).start();

    }

    private void stopRecord() {
        recordingStarted = false;
        audioRecord.stop();

        System.out.println("Stopped recording streamId=" + streamId);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);

            // Signed in successfully, show authenticated UI.
            System.out.println("Signed in " + account.getEmail());
            idToken = account.getIdToken();
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.action_record).setVisibility(View.VISIBLE);
            signedIn = true;
            start();
            //updateUI(account);
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            //Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            //textView.append("Failed to Sign in here 23  " + e.getMessage());
            //updateUI(null);
        }
    }
}