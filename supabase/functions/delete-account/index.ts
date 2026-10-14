import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

  try {
    const url = Deno.env.get("SUPABASE_URL");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    if (!url || !anonKey || !serviceKey) throw new Error("Supabase runtime secrets are missing.");

    const authorization = req.headers.get("Authorization") ?? "";
    const userClient = createClient(url, anonKey, {
      global: { headers: { Authorization: authorization } },
      auth: { persistSession: false },
    });
    const adminClient = createClient(url, serviceKey, { auth: { persistSession: false } });
    const { data, error: userError } = await userClient.auth.getUser();
    if (userError || !data.user) throw new Error("로그인이 필요합니다.");
    const user = data.user;
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
