float beamHG(float cosine, float asymmetry) {
    return (1.0 - asymmetry * asymmetry) / (12.566370614359172 * pow(1.0 + asymmetry * asymmetry - 2.0 * asymmetry * cosine, 1.5));
}
float beamPhase(float cosine) {
    return 0.8 * beamHG(cosine, 0.7) + 0.2 * beamHG(cosine, -0.3);
}
float beamDensity(float rain, float thunder, float fogEnd, float snow) {
    return 0.002 + 0.03 * rain * (snow > 0.5 ? 1.5 : 1.0) + 0.02 * thunder + 1.5 / max(fogEnd, 16.0);
}
float beamBilateral(float centreDepth, float neighborDepth) {
    return exp(-abs(centreDepth - neighborDepth) * 2.0);
}
