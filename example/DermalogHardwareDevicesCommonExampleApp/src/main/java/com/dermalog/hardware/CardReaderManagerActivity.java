package com.dermalog.hardware;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

public class CardReaderManagerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_card_reader_manager);
        setTitle("Card Reader Manager");

        ListView list = (ListView) findViewById(R.id.list);
        final CardSlotAdapter adapter = new CardSlotAdapter(DeviceManager.getDevice(this).getCardReaderManager().getSupportedSlots());
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String slot = (String) adapter.getItem(position);
                Intent intent = new Intent(CardReaderManagerActivity.this, CardReaderActivity.class);
                intent.putExtra("SLOT", slot);
                startActivity(intent);
            }
        });
    }

    private class CardSlotAdapter extends BaseAdapter {
        List<String> slots;

        CardSlotAdapter(List<String> slots) {
            this.slots = slots;
        }

        @Override
        public int getCount() {
            return slots.size();
        }

        @Override
        public Object getItem(int position) {
            return slots.get(position);
        }

        @Override
        public long getItemId(int position) {
            return slots.get(position).hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.card_slot_entry, parent, false);
            }

            String slot = (String) getItem(position);
            ((TextView) convertView.findViewById(R.id.name)).setText(slot);
            return convertView;
        }
    }
}
