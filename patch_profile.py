with open("app/src/main/java/com/cityscape/app/ui/profile/ProfileFragment.java", "r") as f:
    content = f.read()

import_str = """import android.view.View;
import android.widget.ImageButton;
import com.cityscape.app.api.ApiClient;
import com.cityscape.app.api.ApiService;
import com.google.gson.JsonObject;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.widget.Toast;"""

if "import com.cityscape.app.api.ApiClient" not in content:
    content = content.replace("import android.view.View;", import_str)

button_str = """
            View settingsButton = view.findViewById(R.id.btn_settings);
            if (settingsButton != null) {
                settingsButton.setOnClickListener(v -> {
                    startActivity(new Intent(requireContext(), SettingsActivity.class));
                });
            }

            ImageButton btnFollowRequests = view.findViewById(R.id.btn_follow_requests);
            if (btnFollowRequests != null) {
                btnFollowRequests.setOnClickListener(v -> {
                    ApiService api = ApiClient.getClient(requireContext()).create(ApiService.class);
                    api.getFollowRequests(sessionManager.getUserId()).enqueue(new Callback<List<JsonObject>>() {
                        @Override
                        public void onResponse(Call<List<JsonObject>> call, Response<List<JsonObject>> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                if (response.body().isEmpty()) {
                                    Toast.makeText(requireContext(), "Nu ai cereri noi.", Toast.LENGTH_SHORT).show();
                                } else {
                                    FollowRequestsDialog dialog = new FollowRequestsDialog(response.body());
                                    dialog.show(getParentFragmentManager(), "FollowRequestsDialog");
                                }
                            }
                        }

                        @Override
                        public void onFailure(Call<List<JsonObject>> call, Throwable t) {
                            Toast.makeText(requireContext(), "Eroare la încărcarea cererilor.", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
"""

start_idx = content.find("View settingsButton = view.findViewById(R.id.btn_settings);")
end_idx = content.find("if (settingsButton != null) {", start_idx)
if end_idx != -1:
    end_idx = content.find("}", end_idx) + 1
    content = content[:start_idx] + button_str + content[end_idx:]

with open("app/src/main/java/com/cityscape/app/ui/profile/ProfileFragment.java", "w") as f:
    f.write(content)

print("Profile Patched!")
