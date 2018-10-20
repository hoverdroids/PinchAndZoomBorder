package com.hoverdroids.pinchandzoomborder;

import android.content.Intent;
import android.net.Uri;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        applyHoverDroidsBranding();
		
		//Before anything can be used in the imageView, a background image bust be set
        PinchZoomImageView imageView = findViewById(R.id.imageview);
        imageView.initImageResource(R.drawable.hoverdroids);
    }

    //None of this is essential for the example. It is only used to brand HoverDroids examples
    private void applyHoverDroidsBranding(){
        //Show app icon
        ActionBar actBar = getSupportActionBar();
        if(actBar != null) {
            actBar.setDisplayShowHomeEnabled(true);
            actBar.setIcon(R.mipmap.ic_ab_hoverdroids);
            actBar.setTitle(R.string.ab_title);
        }

        //Set actions to take when the AB is clicked
        Toolbar ab = findViewById(R.id.action_bar);
        if(ab != null){
            for (int i= 0; i < ab.getChildCount(); i++){
                setActionBarChildActions(ab.getChildAt(i));
            }
        }
    }

    private void setActionBarChildActions(View child){
        //Send users to HoverDroids.com when the title or icon are clicked
        if(child instanceof TextView || child instanceof ImageView) {
            child.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String url = "http://www.HoverDroids.com";

                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(url));
                    startActivity(i);
                }
            });
        }
    }
}