package com.android.onyx.yuanfudaodemo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.android.onyx.yuanfudaodemo.databinding.ActivityMainBinding;
import com.android.onyx.yuanfudaodemo.scribble.ScribbleActivity;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    enum FUNCTION {
        SCRIBBLE(ScribbleActivity.class);

        private Class targetActivity;

        FUNCTION(Class targetActivity) {
            this.targetActivity = targetActivity;
        }

        public Class getTargetActivity() {
            return targetActivity;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        initView();
    }

    private void initView() {
        for (FUNCTION value : FUNCTION.values()) {
            Button button = new Button(this);
            button.setText(value.name());
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    go(value.getTargetActivity());
                }
            });
            binding.container.addView(button);
        }
    }

    private void go(Class targetActivity) {
        startActivity(new Intent(this, targetActivity));
    }
}