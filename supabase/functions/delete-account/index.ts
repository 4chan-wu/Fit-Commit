import { corsHeaders, errorMessage, json } from "../_shared/http.ts";
import { requireUser } from "../_shared/supabase.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const { user, adminClient } = await requireUser(req);
    const bucket = Deno.env.get("SUPABASE_CLOTHING_BUCKET") ?? "clothing-images";

    const { data: categoryFolders, error: listError } = await adminClient.storage
      .from(bucket)
      .list(user.id, { limit: 1000 });
    if (listError) throw listError;

    for (const folder of categoryFolders ?? []) {
      const categoryPath = `${user.id}/${folder.name}`;
      const { data: objects, error: objectError } = await adminClient.storage
        .from(bucket)
        .list(categoryPath, { limit: 1000 });
      if (objectError) throw objectError;
      const paths = (objects ?? [])
        .filter((object) => object.id)
        .map((object) => `${categoryPath}/${object.name}`);
      if (paths.length > 0) {
        const { error: removeError } = await adminClient.storage.from(bucket).remove(paths);
        if (removeError) throw removeError;
      }
    }

    const { error: deleteError } = await adminClient.auth.admin.deleteUser(user.id);
    if (deleteError) throw deleteError;
    return json({ deleted: true });
  } catch (error) {
    console.error(errorMessage(error));
    return json({ error: errorMessage(error) }, 400);
  }
});
