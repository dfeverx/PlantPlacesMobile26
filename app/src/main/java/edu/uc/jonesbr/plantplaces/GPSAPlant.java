package edu.uc.jonesbr.plantplaces;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import edu.uc.jonesbr.plantplaces.dao.GetPlantService;
import edu.uc.jonesbr.plantplaces.dao.IPlantDAO;
import edu.uc.jonesbr.plantplaces.dao.PlantDAOStub;
import edu.uc.jonesbr.plantplaces.dao.PlantJSONDAO;
import edu.uc.jonesbr.plantplaces.dao.RetrofitClientInstance;
import edu.uc.jonesbr.plantplaces.dto.PlantDTO;
import edu.uc.jonesbr.plantplaces.dto.PlantList;
import edu.uc.jonesbr.plantplaces.dto.SpecimenDTO;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GPSAPlant extends PlantPlacesActivity implements GestureDetector.OnGestureListener {

    public static final int CAMERA_PERMISSION_REQUEST_CODE = 1996;
    public static final int CAMERA_REQUEST_CODE = 1995;
    public static final int ONE_MINUTE = 60000;
    public static final int ACCESS_FINE_LOCATION_REQUEST_CODE = 1995;
    public static final int SWIPE_THRESHOLD = 100;
    public static final int SWIPE_VELOCITY_THRESHOLD = 100;
    public static final int AUTHORIZATION_REQUEST_CODE = 1994;

    private FirebaseUser user;
    private Uri uri;
    
    @BindView(R.id.actPlantName)
    AutoCompleteTextView actPlantName;

    @BindView(R.id.actLocation)
    AutoCompleteTextView actLocation;

    @BindView(R.id.edtDescription)
    EditText edtDescription;

    @BindView(R.id.txtLatitude)
    TextView txtLongitude;

    @BindView(R.id.txtLongitude)
    TextView txtLatitude;

    @BindView(R.id.chronGPS)
    Chronometer chronoGPS;

    @BindView(R.id.btnPause)
    ImageButton btnPause;

    private LocationRequest locationRequest;
    private double longitude;
    private double latitude;
    private boolean updatesRequested = true;
    private ProgressDialog progressDialog;
    private int plantGuid;
    private String string;
    private String plantString;
    private boolean knownPlant;
    private GestureDetector gestureDetector;
    private FusedLocationProviderClient client;
    private LocationCallback locationCallback;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gpsaplant);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ButterKnife.bind(this);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        // declare and register our BroadcastReceiver.
        BroadcastReceiver br = new SynchronizeReceiver();

        // add an intent filter to indicate which intents we are intereseted in receiving
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);

        this.registerReceiver(br, filter);

        client = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                chronoGPS.stop();
                chronoGPS.setBase(SystemClock.elapsedRealtime());
                longitude = locationResult.getLastLocation().getLongitude();
                latitude = locationResult.getLastLocation().getLatitude();
                txtLatitude.setText(Double.toString(latitude));
                txtLongitude.setText(Double.toString(longitude));
                // restart the chronometer.
                chronoGPS.start();
            }

            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                super.onLocationAvailability(locationAvailability);
            }
        };

        locationRequest = new LocationRequest();
        locationRequest.setInterval(ONE_MINUTE);
        locationRequest.setFastestInterval(ONE_MINUTE/4);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // start looking for plants to populate autocomplete
        PlantSearchTask plantSearchTask = new PlantSearchTask();
        plantSearchTask.execute("e");

        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        DatabaseReference reference = firebaseDatabase.getReference();
        reference.child("specimens")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Iterable<DataSnapshot> children = dataSnapshot.getChildren();

                        for (DataSnapshot child: children)
                        {
                            SpecimenDTO specimenDTO = child.getValue(SpecimenDTO.class);
                            Toast.makeText(GPSAPlant.this, "Data: " + specimenDTO.toString(), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });

        PlantSelected plantSelected = new PlantSelected();
        actPlantName.setOnItemSelectedListener(plantSelected);
        actPlantName.setOnItemClickListener(plantSelected);
        actPlantName.setOnFocusChangeListener(plantSelected);

        gestureDetector = new GestureDetector(this);
    }


    @OnClick(R.id.btnPause)
    public void toggleUpdates() {
        if (updatesRequested) {
            removeLocationUpdates();
            updatesRequested = false;
            // change the icon to indicate that location updates are playable.
            btnPause.setImageDrawable(getDrawable(R.drawable.ic_play));
        } else {
            prepRequestLocationUpdates();
            updatesRequested = true;
            // show a pause icon
            btnPause.setImageDrawable(getDrawable(R.drawable.ic_pause));
        }
    }

    private void prepRequestLocationUpdates() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // if the user has already given us this permission, then request updates.
            requestLocationUpdates();
        } else {
            // the user has revoked permission, or never gave us permission, so let's request it.
            String [] permissionRequest = {Manifest.permission.ACCESS_FINE_LOCATION};
            requestPermissions(permissionRequest, ACCESS_FINE_LOCATION_REQUEST_CODE);
        }

    }

    private void requestLocationUpdates() {
        client.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
    }

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {

    }

    @Override
    public boolean onFling(MotionEvent downEvent, MotionEvent moveEvent, float velocityX, float velocityY) {
        boolean result = false;
        float diffY = moveEvent.getY() - downEvent.getY();
        float diffX = moveEvent.getX() - downEvent.getX();
        // which was greater?  movement across Y or X?
        if (Math.abs(diffX) > Math.abs(diffY)) {
            // right or left swipe
            if (Math.abs(diffX)> SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffX > 0) {
                    onSwipeRight();
                } else {
                    onSwipeLeft();
                }
                result = true;
            }
        } else {
            // up or down swipe
            if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY)> SWIPE_VELOCITY_THRESHOLD) {
                if (diffY > 0) {
                    onSwipeBottom();
                } else {
                    onSwipeTop();
                }
                result = true;
            }
        }

        return result;
    }

    private void onSwipeTop() {
        Toast.makeText(this, "Swipe Top", Toast.LENGTH_LONG).show();
    }

    private void onSwipeBottom() {
        Toast.makeText(this, "Swipe Bottom", Toast.LENGTH_LONG).show();
    }

    private void onSwipeLeft() {
        Toast.makeText(this, "Swipe Left", Toast.LENGTH_LONG).show();
    }

    private void onSwipeRight() {
        Toast.makeText(this, "Swipe Right", Toast.LENGTH_LONG).show();
        saveSpecimen();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    class UndoListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            // this is the method that will be invoked when the user clicks on the snackbar's action
            String plantName = actPlantName.getText().toString();
            String location = actLocation.getText().toString();
            Toast.makeText(GPSAPlant.this, "Plant Name: " + plantName + " Location " + location, Toast.LENGTH_LONG).show();

        }
    }

    @OnClick(R.id.btnOpen)
    public void goToColorCapture() {


    }

    @OnClick(R.id.btnSave)
    public void saveSpecimen() {
        if (user != null) {
            // if the user is not null, then the user has authenticated already.
            saveSpecimenToFirebase();
        } else {
            // we need to authenticate
            List<AuthUI.IdpConfig> providers = Arrays.asList(new AuthUI.IdpConfig.EmailBuilder().build(),
                    new AuthUI.IdpConfig.GoogleBuilder().build());
            startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder().
                    setAvailableProviders(providers).build(), AUTHORIZATION_REQUEST_CODE);
        }
    }

    private void saveSpecimenToFirebase() {
        final SpecimenDTO specimenDTO = new SpecimenDTO();
        specimenDTO.setPlantName(actPlantName.getText().toString());
        specimenDTO.setLatitude(Double.toString(latitude));
        specimenDTO.setLongitude(Double.toString(longitude));
        specimenDTO.setDescription(edtDescription.getText().toString());
        specimenDTO.setLocation(actLocation.getText().toString());
        specimenDTO.setPlantId(plantGuid);

        FirebaseDatabase firebaseDatabase = FirebaseDatabase.getInstance();
        final DatabaseReference reference = firebaseDatabase.getReference();

        if (uri != null) {
            StorageReference storageReference = FirebaseStorage.getInstance().getReference();
            final StorageReference imageRef = storageReference.child("images/" + uri.getLastPathSegment());
            UploadTask uploadTask = imageRef.putFile(uri);

            uploadTask.addOnFailureListener(new OnFailureListener() {

                @Override
                public void onFailure(@NonNull Exception e) {
                    int i = 1 + 1;
                    // TODO properly handle this error.
                }
            });

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {

                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    // this is where we will end up if our image uploads successfully.
                    StorageMetadata snapshotMetadata = taskSnapshot.getMetadata();
                    Task<Uri> downloadUrl = imageRef.getDownloadUrl();
                    downloadUrl.addOnSuccessListener(new OnSuccessListener<Uri>() {

                        @Override
                        public void onSuccess(Uri uri) {
                            String imageReference = uri.toString();
                            reference.child("specimens").child(specimenDTO.getKey()).child("imageUrl").setValue(imageReference);
                            specimenDTO.setImageUrl(imageReference);
                        }
                    });
                }
            });
        }

        DatabaseReference specimenReference = reference.child("specimens").push();
        specimenDTO.setImageUrl(" ");
        specimenReference.setValue(specimenDTO);
        // update the DTO with the Firebase generated key.
        String key = specimenReference.getKey();
        specimenDTO.setKey(key);


    }

    @OnClick(R.id.btnPhoto)
    public void takePhoto() {
        prepInvokeCamera();
    }

    private void prepInvokeCamera() {
        // permissions check.
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            invokeCamera();
        } else {
            String[] permissionRequest = {Manifest.permission.CAMERA};
            requestPermissions(permissionRequest, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    private void invokeCamera() {
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // path to th eimages directory.
        File imagePath = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        // create an image file at this path.
        File picFile = new File(imagePath, getPictureName());

        // convert file to URI
        uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", picFile);
        // where do I want to save the image?
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
        // pass permission to the camera
        cameraIntent.setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);

    }

    private String getPictureName() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date now = new Date();
        String timestamp = sdf.format(now);
        // assemble a picture name
        String pictureName = "image" + timestamp + ".jpg";
        return pictureName;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA_REQUEST_CODE) {
                Toast.makeText(this, R.string.picturesaved, Toast.LENGTH_LONG).show();
            } else if (requestCode == AUTHORIZATION_REQUEST_CODE) {
                user = FirebaseAuth.getInstance().getCurrentUser();
                saveSpecimenToFirebase();
            }
        } else if (resultCode == RESULT_CANCELED) {
            if (requestCode == AUTHORIZATION_REQUEST_CODE) {
                Toast.makeText(this, "Cannot save without authentication", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            // we are hearing back from the camera.
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                invokeCamera();
            } else {
                Toast.makeText(this, R.string.nocamerapermission, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == ACCESS_FINE_LOCATION_REQUEST_CODE) {
            // if we are here, we are hearing back from the location permission.
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // user gave us permission.
                requestLocationUpdates();
            } else {
                Toast.makeText(this, R.string.nogpspermission, Toast.LENGTH_LONG).show();
            }
        }

    }

    @Override
    protected int getCurrentMenuId() {
        return R.id.gpsAPlant;
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        prepRequestLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeLocationUpdates();
    }

    private void removeLocationUpdates() {
        client.removeLocationUpdates(locationCallback);
    }

    class PlantSearchTask extends AsyncTask<String, Integer, List<PlantDTO>> {

        @Override
        protected void onPostExecute(List<PlantDTO> plantDTOS) {
            super.onPostExecute(plantDTOS);
            // adapt the data to be UI friendly.
            ArrayAdapter<PlantDTO> plantAdapter = new ArrayAdapter<PlantDTO>
                    (GPSAPlant.this, android.R.layout.simple_list_item_1, plantDTOS);
            // associate the data with the auto complete text view.
            actPlantName.setAdapter(plantAdapter);
            progressDialog.dismiss();
        }

        @Override
        protected List<PlantDTO> doInBackground(String... searchTerms) {
            List<PlantDTO> allPlants = new ArrayList<PlantDTO>();
            // declare a variable for our DAO class that will do a lot of the networking for us.
            IPlantDAO plantDAO = new PlantJSONDAO();
            String searchTerm = searchTerms[0];
            try {
                allPlants = plantDAO.search(searchTerm);

                int plantCounter = 0;

                // iterate over all of the plants we fetched, and place them into the local database.
                for (PlantDTO plant  : allPlants) {
                    // act like we're saving to the database.
                    plantCounter++;

                    if (plantCounter % (allPlants.size() / 25) == 0) {
                        // update progress
                        publishProgress(plantCounter * 100 / allPlants.size());
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return allPlants;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            // setup a progress dialog
            progressDialog = new ProgressDialog(GPSAPlant.this);
            progressDialog.setCancelable(true);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setMax(100);
            progressDialog.setMessage(getString(R.string.downloadingPlantNames));

            // make a button
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            progressDialog.show();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            progressDialog.setProgress(values[0]);
        }
    }

    class PlantSelected implements AdapterView.OnItemSelectedListener, AdapterView.OnItemClickListener, View.OnFocusChangeListener {

        @Override
        public void onFocusChange(View view, boolean b) {
            String currentPlantName = actPlantName.getText().toString();
            if(!currentPlantName.isEmpty() && !currentPlantName.equals(plantString)) {
                // we are in a new, undefined plant.
                // navigate to a screen where the user can enter a new plant.
                Toast.makeText(GPSAPlant.this, "New Plant!", Toast.LENGTH_LONG).show();
                plantGuid = 0;
                knownPlant = false;
            }
        }

        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int position, long row) {
            PlantDTO plant = (PlantDTO) actPlantName.getAdapter().getItem(position);
            plantGuid = plant.getGuid();
            plantString = actPlantName.getText().toString();
            knownPlant = true;
        }

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int position, long row) {
            PlantDTO plant = (PlantDTO) actPlantName.getAdapter().getItem(position);
            plantGuid = plant.getGuid();
            plantString = actPlantName.getText().toString();
            knownPlant = true;
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }
    }

}

