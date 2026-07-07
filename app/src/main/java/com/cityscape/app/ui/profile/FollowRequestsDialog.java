package com.cityscape.app.ui.profile;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.cityscape.app.R;
import com.cityscape.app.api.ApiClient;
import com.cityscape.app.api.ApiService;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FollowRequestsDialog extends BottomSheetDialogFragment {

    private List<JsonObject> requests;
    private ListView listView;

    public FollowRequestsDialog(List<JsonObject> requests) {
        this.requests = requests;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_follow_requests, container, false);
        listView = view.findViewById(R.id.list_requests);
        
        RequestAdapter adapter = new RequestAdapter(requireContext(), requests);
        listView.setAdapter(adapter);

        return view;
    }

    private class RequestAdapter extends ArrayAdapter<JsonObject> {
        public RequestAdapter(Context context, List<JsonObject> objects) {
            super(context, 0, objects);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_follow_request, parent, false);
            }

            JsonObject req = getItem(position);
            TextView name = convertView.findViewById(R.id.req_name);
            Button btnAccept = convertView.findViewById(R.id.btn_accept);
            Button btnReject = convertView.findViewById(R.id.btn_reject);

            name.setText(req.get("name").getAsString() + " vrea să te urmărească.");

            btnAccept.setOnClickListener(v -> respond(req, "accept", position));
            btnReject.setOnClickListener(v -> respond(req, "reject", position));

            return convertView;
        }

        private void respond(JsonObject req, String action, int position) {
            ApiService api = ApiClient.getClient().create(ApiService.class);
            Map<String, String> data = new HashMap<>();
            data.put("request_id", req.get("request_id").getAsString());
            data.put("action", action);

            api.respondFollowRequest(data).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    if (response.isSuccessful()) {
                        Toast.makeText(getContext(), "Cerere " + (action.equals("accept") ? "acceptată" : "respinsă"), Toast.LENGTH_SHORT).show();
                        requests.remove(position);
                        notifyDataSetChanged();
                        if (requests.isEmpty()) dismiss();
                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    Toast.makeText(getContext(), "Eroare rețea", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
