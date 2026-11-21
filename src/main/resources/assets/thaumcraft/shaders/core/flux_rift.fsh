#version 150

#define M_PI 3.1415926535897932384626433832795

uniform sampler2D Sampler0;
uniform int time;
uniform float yaw;
uniform float pitch;

in vec3 position;
in vec4 vertexColor;
out vec4 fragColor;

mat4 rotationMatrix(vec3 axis, float angle) {
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;
    return mat4(
        oc * axis.x * axis.x + c,          oc * axis.x * axis.y - axis.z * s, oc * axis.z * axis.x + axis.y * s, 0.0,
        oc * axis.x * axis.y + axis.z * s, oc * axis.y * axis.y + c,          oc * axis.y * axis.z - axis.x * s, 0.0,
        oc * axis.z * axis.x - axis.y * s, oc * axis.y * axis.z + axis.x * s, oc * axis.z * axis.z + c,          0.0,
        0.0,                              0.0,                              0.0,                              1.0
    );
}

void main() {
    vec4 color = vec4(0.044, 0.036, 0.063, 1.0);
    vec4 direction = normalize(vec4(-position, 0.0));

    float sinPitch = sin(pitch);
    float cosPitch = cos(pitch);
    direction = normalize(vec4(
        direction.x,
        direction.y * cosPitch - direction.z * sinPitch,
        direction.y * sinPitch + direction.z * cosPitch,
        0.0
    ));

    float sinYaw = sin(-yaw);
    float cosYaw = cos(-yaw);
    direction = normalize(vec4(
        direction.z * sinYaw + direction.x * cosYaw,
        direction.y,
        direction.z * cosYaw - direction.x * sinYaw,
        0.0
    ));

    for (int layer = 0; layer < 16; layer++) {
        int multiplier = 16 - layer;
        int j = layer + 7;
        float randomA = float(j * j * 4321 + j * 8) * 2.0;
        int k = j + 1;
        float randomB = float(k * k * k * 239 + k * 37) * 3.6;
        float randomC = randomA * 347.4 + randomB * 63.4;

        vec3 axis = normalize(vec3(sin(randomA), sin(randomB), cos(randomC)));
        vec4 ray = direction * rotationMatrix(axis, mod(randomC, 2.0 * M_PI));
        float u = 0.5 + atan(ray.z, ray.x) / (2.0 * M_PI);
        float v = 0.5 + asin(ray.y) / M_PI;

        float scale = float(multiplier) * 0.5 + 2.75;
        vec2 uv = vec2(u * scale, (v + float(time) * 0.00006) * scale * 0.6);
        vec4 sampleColor = texture(Sampler0, uv);
        float alpha = sampleColor.r
                * (0.05 + (1.0 / float(multiplier)) * 0.65)
                * (1.0 - smoothstep(0.15, 0.48, abs(v - 0.5)));

        float red = (mod(randomA, 29.0) / 29.0) * 0.5 + 0.1;
        float green = (mod(randomB, 35.0) / 35.0) * 0.5 + 0.4;
        float blue = (mod(randomA, 17.0) / 17.0) * 0.5 + 0.5;
        color = color * (1.0 - alpha) + vec4(red, green, blue, 1.0) * alpha;
    }

    fragColor = color * vertexColor;
}
