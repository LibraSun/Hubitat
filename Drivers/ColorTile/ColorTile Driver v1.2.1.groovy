/*
 * ================================ ColorTile ========================================
 *
 *  Copyright 2024 LibraSun Enterprises LLC - All Rights Reserved
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 *  file except in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under
 *  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 *  either express or implied.
 *  See the License for specific language governing permissions and limitations under the License.
 *
 * =====================================================================================
 *
 *  PURPOSE
 *  Create colorful dashboard tiles for use as backgrounds and/or foregrounds
 *  offering the user several activation, customization and animation options.
 *
 *  Last modified: 2024-OCT-15

 INSPIRATION
 Hubitat staff and Community Forum members:
   @SBurke, @GaryJMilne, @thebearmay, @Bertabcd1234, 
   @lcw731, @grantslender, @csteele and many others!

 REVISION HISTORY
 DATE       | REV    | AUTHOR     | NOTES
 2024-10-01 | v0.0.1 | LibraSun   | Initial concept and framework
 2024-10-03 | v0.0.2 | LibraSun   | Compose SVG elements in VSCode
 2024-10-05 | v0.0.3 | LibraSun   | Define color themes, test run
 2024-10-11 | v1.0.1 | LibraSun   | Initial testing suite aboard hub
 2024-10-12 | v1.0.2 | LibraSun   | Construct inputs and preferences
 2024-10-13 | v1.0.3 | LibraSun   | Instantiate required methods
 2024-10-14 | v1.0.5 | GaryJMilne | Normalize data structuring
 2024-10-14 | v1.0.6 | LibraSun   | Completed initial ToDo list
 2024-10-14 | v1.0.7 | TheBearMay | Improved state input/output
 2024-10-15 | v1.0.8 | LibraSun   | Reduce memory overhead
 2024-10-15 | v1.1.0 | LibraSun   | Add realtime coloring
 2024-10-15 | v1.2.0 | LibraSun   | Refactor color database
 2024-10-16 | v1.2.1 | LibraSun   | Refactor program flow

TODO
 ✅ Convert Fields to state variables
 ✅ Include input validation
 ✅ Expand event logging
 ✅ Generate colors in realtime (vs presets)
 ☐ Trial testing and monitor in-use CPU load

STRETCH GOALS
 ✅ Add ID= label to every SVG graphic component
 ☐ Add "OnlyPreset/AutoShade/RangeStops" infill
 ☐ Add animations for Foreground Overlay tiles
 ☐ Allow button() actions to set HARP() colors
 ✅ Add setColor() for saving specified color
 ☐ Add CSS animation effects (Pulse, etc.)
 ☐ Respond to transition duration command
 ☐ Add alternating 2-color strobe effect
 ☐ Allow save/clear specific user color
 ✅ Enable HSV -> RGB color conversion
 ☐ Add 'Undo' feature using state map
 ☐ Add custom shape/tile dimensions
 ✅ Add custom alpha/transparency
 ☐ Allow saving custom SVG scenes
 ☐ Divide into smaller modules
*/

// Report current version
static final String version() { return "1.2.1" }

// Device driver definition

metadata {
  definition (
    name: "ColorTile",
    namespace: "LibraSunLLC",
    author: "@LibraSun",
    category: "dashboard",
    importUrl: "https://github.com/LibraSun/Hubitat/blob/main/Drivers/ColorTile/ColorTile%20Driver%20v1.2.1.groovy",
    documentationUrl: "",
    singleThreaded: true // for memory efficiency
  ) {
    capability "Actuator"
    capability "ColorControl"
    capability "PushableButton"
    capability "Switch"
    capability "SwitchLevel"

    command "hold", [[name: "Button number*", type: "NUMBER", description: "Button 1 — 256"]]
    command "push", [[name: "Button number*", type: "NUMBER", description: "Button 1 — 256"]]
    command "release", [[name: "Button number*", type: "NUMBER", description: "Button 1 — 256"]]
    command "saveLevelColor", [[name: "Color String*", type: "STRING", description: "(#Hex, °K or Name)"],[name: "Save for Level*", type: "NUMBER", description: "(level number)"]]

    attribute "level", "NUMBER"
    attribute "switch", "ENUM", ["off", "on"]
    attribute "Current Color", "STRING"
    attribute "Log", "STRING"
    attribute "Tile Output", "STRING"
    attribute "FAQ", "STRING"
    }
    preferences {
      input (name: "colorTheme", type: "enum", multiple: false, options: ["Grays","Reds","Greens","Blues","Cyans","Magentas","Yellows"], title: "Color Theme", description: "(pick a color family)", defaultValue: "Grays", required: true)
      input (name: "shapeType", type: "enum", multiple: false, options: ["Square", "Circle"], title: "Tile Shape", description: "(select)", defaultValue: "Square", required: true)
      input (name: "lineWt", type: "enum", multiple: false, options: [["1":"1px - Thin"],["3":"3px - Default"],["7":"7px - Medium"],["12":"12 px - Thick"]], title: "Line Thickness", description: "(in pixels)", defaultValue: "3", required: true)
      input (name: "fillType", type: "enum", multiple: false, options: [["0":"Outline"],["1":"Solid"]], title: "Shape Fill", description: "(select)", defaultValue: "1", required: true)
      input (name: "tileAlpha", type: "number", range: [0..100], title: "Tile Opacity", description: "(0=clear ~ 100=normal)", defaultValue: 100, required: true)
      input (name: "flashReps", type: "enum", multiple: false, options: [["none":"None"],["1":"1x"],["2":"2x"],["5":"5x"],["30":"30x"],["indefinite":"Forever"]], title: "Repeat Flash", description: "(how many times)", defaultValue: "1", required: true)
      input (name: "flashGaps", type: "enum", multiple: false, options: [["0.4s":"0.2s"],["1.0s":"0.5s"],["1.5s":"0.75s"],["2.0s":"1.0s"],["4.0s":"2.0s"]], title: "Flash Timing", description: "(in seconds)", defaultValue: "0.4s", required: true)
      input (name: "resetColors", type: "enum", multiple: false, options: ["No","Yes"], title: "Reset User Colors", description: "(are you sure?)", defaultValue: "No", required: false)
      input (name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false)
      input (name: "txtEnable", type: "bool", title: "Enable descriptive logging", defaultValue: true)
    }
}

import hubitat.helper.ColorUtils

// Create tile output according to last device action
void createTileOutput (String noun, String verb, def object) {
  // Select user or (if undefined) theme color
  String stateColor
  switch (noun) { // expand for button actions (push/hold/release)
    case "Button": // check for valid 'button' value TODO
      stateColor = state.userColors.Button["$verb"]["$object"]
        // implement HARP() colors for buttons TODO
      break
    case "Level": // check for valid 'level' value TODO
      stateColor = state.userColors.Level["$object"]
      break
    case "Switch":
      stateColor = state.userColors.Switch["$object"]
      break
    default:
      break
  }
  stateColor = stateColor ?: setThemeColor("$object") // if no match, use theme color
  sendEvent(name: "Current Color", value: "$stateColor", descriptionText: "ColorTile is $stateColor", isStateChange:true)
  
  // Create flashing animation
  String flashTiming
  flashReps = flashReps ?: "none"
  flashGaps = flashGaps ?: "0.4s"
  if (flashReps != "none") {
    flashTiming = """<animate attributeName="opacity" values="0;1" dur="$flashGaps" repeatCount="$flashReps"></animate>"""
  }

  //Generate graphic from SVG components
  String tileShape
  String shapeFill
  shapeType = shapeType ?: "Square"
  lineWt = lineWt ?: "3"
  shapeFill = (fillType == "0") ? "none" : stateColor
  tileAlpha = tileAlpha ?: 100
  
  if (tileAlpha < 0 || tileAlpha > 100) {tileAlpha = 100} // Validate opacity range 0..100
  switch (shapeType) {
    case "Square":
      tileShape = """<rect id="ctsq" x="0" y="0" width="100%" height="100%" fill="$shapeFill" stroke-width="$lineWt" stroke="$stateColor"></rect>"""
      break
    case "Circle":
    def radius = 49 - lineWt.toInteger() / 3
    tileShape = """<circle id="ctcl" cx="50%" cy="50%" r="$radius%" fill="$shapeFill" stroke-width="$lineWt" stroke="$stateColor"></circle>"""
      break
    default:
      tileShape = """<rect id="ctsq" x="0" y="0" width="100%" height="100%" fill="$shapeFill" stroke-width="$lineWt" stroke="$stateColor"></rect>"""
  }

  String tileOutput = """
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 150 150" width="100%">
    <g id="ctgrp" opacity="$tileAlpha%">
    $tileShape
    </g>
    $flashTiming
    <g id="${now()}"></g>
    </svg>
  """
  // device.deleteCurrentState("Tile Output") // prevent duplication on Details Page?
  sendEvent(name: "Tile Output", value: "$tileOutput", descriptionText: "ColorTile turned $stateColor", isStateChange:true)
  if (txtEnable) log.info "ColorTile turned $stateColor"
}

// Save custom color for specified level
void saveLevelColor(String color, def level = -1) {
  if (level >= 0) {
    color = color.trim()
    level = "$level".trim()
    state.lastAction = [noun: "Color", verb: "saved for level", object: "$level"]
    if ( isHexRGB(color) ) { // color in RGB hex format
      setUserColor(color)
    } else if ( isTempK(color) ) { // color temperature in °K format
      int colorTemp = color.findAll(/\d+/)[0] as Integer
      String colorName = convertTemperatureToGenericColorName(colorTemp)
      setUserColor(colorName)
    } else if ( isNameCSS(color) ){ // colorname in CSS style
      setUserColor(color)
    } else {
      sendEvent(name: "Log", value: "Color $color not recognized", descriptionText: "ColorTile did not recognize color $color", isStateChange:true)
      if (debugEnable) log.warn "ColorTile did not recognize color $color"
    }
  } else {
      sendEvent(name: "Log", value: "Level $level not recognized", descriptionText: "ColorTile did not recognize level $level", isStateChange:true)
      if (debugEnable) log.warn "ColorTile did not recognize level $level"
  }
}

// Set and save specified color
void setUserColor(String color) { // Parse input to rgba() format TODO
  Map lastAction = state?.lastAction ?: [noun: "Action", verb: "is", object: "missing"]
  switch (lastAction.noun) {
    case "Action":
      sendEvent(name: "Log", value: "SAVE FAILED: Must select action (button, level, or switch) first", descriptionText: "ColorTile save failed", isStateChange:true)
    if (debugEnable) log.warn "ColorTile save failed due to missing action"
      break
    case "Color": // last action was Save Level Color
      state.userColors.Level["$lastAction.object"] = color
      sendEvent(name: "Current Color", value: "$color", descriptionText: "ColorTile saved $color for level $lastAction.object", isStateChange:true)  
      sendEvent(name: "Log", value: "Saved $color for level $lastAction.object", descriptionText: "ColorTile saved $color for level $lastAction.object", isStateChange:true)
      if (txtEnable) log.info "ColorTile saved $color for level $lastAction.object"
      break
    case "Level":
      state.userColors.Level["$lastAction.object"] = color
      sendEvent(name: "Current Color", value: "$color", descriptionText: "ColorTile saved $color for $lastAction.noun action", isStateChange:true)  
      sendEvent(name: "Log", value: "Saved $color for $lastAction.noun $lastAction.verb $lastAction.object", descriptionText: "ColorTile saved $color for $lastAction.noun action $lastAction.verb $lastAction.object", isStateChange:true)
      if (txtEnable) log.info "ColorTile saved $color for $lastAction.noun $lastAction.verb $lastAction.object"
      break
    case "Button":
    state.userColors.Button[lastAction.verb]["$lastAction.object"] = color
      sendEvent(name: "Current Color", value: "$color", descriptionText: "ColorTile saved $color for $lastAction.noun action", isStateChange:true)
      sendEvent(name: "Log", value: "Saved $color for $lastAction.noun $lastAction.verb $lastAction.object", descriptionText: "Saved $color for $lastAction.noun $lastAction.verb $lastAction.object", isStateChange:true)
      if (txtEnable) log.info "ColorTile saved $color for $lastAction.noun $lastAction.verb $lastAction.object"
      break
    case "Switch":
    state.userColors.Switch["$lastAction.object"] = color
      sendEvent(name: "Current Color", value: "$color", descriptionText: "ColorTile saved $color for $lastAction.noun action", isStateChange:true)
      sendEvent(name: "Log", value: "Saved $color for $lastAction.noun $lastAction.verb $lastAction.object", descriptionText: "Saved $color for $lastAction.noun $lastAction.verb $lastAction.object", isStateChange:true)
      if (txtEnable) log.info "ColorTile saved $color for $lastAction.noun $lastAction.verb $lastAction.object"
      break
    default:
      sendEvent(name: "Log", value: "SAVE FAILED: Must select action (button, level, or switch) first", descriptionText: "ColorTile save failed", isStateChange:true)
      if (debugEnable) log.warn "ColorTile save failed due to missing action"
      break
  }
}

// Wipe and initialize color lookup table
void resetUserColors() {
  state.remove("userColors")
  state.userColors = [
    Button: [
      held: [
        1:"red"
      ],
      pushed: [
        1:"blue"
      ],
      released: [
        1:"green"
      ]
    ],
    Level: [
      0: "grey",
      100: "yellow"
    ],
    Switch: [
      off: "darkgrey",
      on: "white"
    ]
  ]
  device.updateSetting("resetColors", [type: "enum", value: "No"])
  sendEvent(name: "Log", value: "User colors reset", descriptionText: "ColorTile reset user colors", isStateChange:true)
    if (txtEnable) log.warn "ColorTile reset user colors"
}

// REQUIRED DRIVER METHODS

// Install virtual device
void installed() {
  state.lastAction = [noun: "Device", verb: "was", object: "installed"]
  resetUserColors()
  long timeNow = now()
  state.updatedAt = timeNow
  state.author = "@LibraSun"
  state.FAQ = ""
  sendEvent(name: "FAQ", value: "<a href='' style='font-size:2em'>❓</a>", descriptionText: "", isStateChange: true)
  sendEvent(name: "Log", value: "ColorTile driver installed", descriptionText: "ColorTile driver installed", isStateChange:true)
  log.info "ColorTile driver installed at $timeNow"
  updated()
}

// Button hold command received
void hold(btnNum) {
  state.lastAction = [noun: "Button", verb: "held", object: btnNum]
  sendEvent(name:"held", value:"${btnNum.toInteger()}", descriptionText:"ColorTile button $btnNum held", isStateChange:true)
  clearAttrLog()
  if (debugEnable) log.info "ColorTile button $btnNum held"
  createTileOutput ("Button", "held", btnNum)
}
                                                     
// Button push command received
void push(btnNum) {
  state.lastAction = [noun: "Button", verb: "pushed", object: btnNum]
  sendEvent(name:"pushed", value:"${btnNum.toInteger()}", descriptionText:"ColorTile button $btnNum pushed", isStateChange:true)
  clearAttrLog()
  if (debugEnable) log.info "ColorTile button $btnNum pushed"
  createTileOutput ("Button", "pushed", btnNum)
}

// Button release command received
void release(btnNum) {
  state.lastAction = [noun: "Button", verb: "released", object: btnNum]
  sendEvent(name:"released", value:"${btnNum.toInteger()}", descriptionText:"ColorTile button $btnNum released", isStateChange:true)
  clearAttrLog()
  if (debugEnable) log.info "ColorTile button $btnNum released"
  createTileOutput ("Button", "released", btnNum)
}

// Switch off command received
void off() {
  state.lastAction = [noun: "Switch", verb: "turned", object: "off"]
  sendEvent(name:"switch", value:"off", descriptionText:"ColorTile switched Off", isStateChange:true)
  clearAttrLog()
  if (debugEnable) log.info "ColorTile switched to Off"
  createTileOutput ("Switch", "turned", "off")
}

// Switch on command received
void on() {
  state.lastAction = [noun: "Switch", verb: "turned", object: "on"]
  sendEvent(name:"switch", value:"on", descriptionText:"ColorTile switched On", isStateChange:true)
  clearAttrLog()
  if (debugEnable) log.info "ColorTile switched to On"
  createTileOutput ("Switch", "turned", "on")
}

// Set level (with optional duration) command received
void setLevel (level, duration = 0) { // enhance operation using duration value? TODO
  state.lastAction = [noun: "Level", verb: "set", object: level]
  sendEvent(name: "level", value: "$level", descriptionText: "ColorTile set to level $level", isStateChange:true)
  clearAttrLog()
  if (debugEnable) log.info "ColorTile set to level $level"
  createTileOutput ("Level", "set", level.toInteger())
}

// Set hue command received
void setHue(def hue) { // ignore
  clearAttrLog()
  if (debugEnable) log.warn "ColorTile ignoring setHue() command"
  sendEvent(name:"Log", value:"Warning: setHue() ignored", descriptionText:"ColorTile ignoring setHue() command", isStateChange:true)
}

// Set saturation command received
void setSaturation(def sat) { //ignore
  clearAttrLog()
  if (debugEnable) log.warn "ColorTile ignoring setSaturation() command"
  sendEvent(name:"Log", value:"Warning: setSaturation() ignored", descriptionText:"ColorTile ignoring setSaturation() command", isStateChange:true)
}

// Driver preferences saved
void updated() {
  state.version = version()
  if (resetColors == "Yes") resetUserColors()
  long timeNow = now()
  state.updatedAt = timeNow
  state.lastAction = [noun: "Device", verb: "was", object: "updated"]
  sendEvent(name: "Log", value: "ColorTile driver updated", descriptionText: "ColorTile driver updated", isStateChange:true)
  if (debugEnable) log.trace "ColorTile driver updated at $timeNow"
}

// UTILITY METHODS

// Generate theme color for given value
String setThemeColor (def lux) {
  lux = lux instanceof Number ? lux : (lux as String).toInteger() ?: 100
  def opacity = tileAlpha ? tileAlpha/100 : 1.0
  // Choose correct RGB filter for color theme
  colorTheme = colorTheme ?: "Grays"
  Map flagsRGB = ["Grays":[1,1,1],
  "Reds":[1,0,0], "Greens":[0,1,0], "Blues":[0,0,1],
  "Cyans":[0,1,1], "Magentas":[1,0,1], "Yellows":[1,1,0]
  ]
  String themeColor = "rgba(${lux*flagsRGB[colorTheme][0]}%,${lux*flagsRGB[colorTheme][1]}%,${lux*flagsRGB[colorTheme][2]}%,$opacity)"
  return themeColor
}

// Convert built-in color picker output to RGB hex format
  // For color settings, see https://docs2.hubitat.com/en/developer/driver/capability-list
void setColor (def color) { 
  def hsv = [color.hue, color.saturation, color.level] // HSL color picker sends HSV format
  def rgb = hubitat.helper.ColorUtils.hsvToRGB(hsv)
  def hex = hubitat.helper.ColorUtils.rgbToHEX(rgb)
  if (debugEnable) log.info "ColorTile converted HSV $hsv to RGB $rgb"
  setUserColor(hex)
}

// Test user input for valid RGB hex color format (#fed #feda #ffeedd #ffeeddaa)
boolean isHexRGB (String color) {
  return color =~ /^#(?:[0-9a-fA-F]{3,4}){1,2}$/
}

// Test user input for valid Kelvin color temperature format (1234°K)
Boolean isTempK (String temp) {
  return temp =~ /\d{1,4}(?:°| )?[Kk](?:°| )?/
}

// Test user input for valid CSS color names
Boolean isNameCSS (String colorname) {
    return colorname =~ /\b(aliceblue|antiquewhite|aqua|aquamarine|azure|beige|bisque|black|blanchedalmond|blue|blueviolet|brown|burlywood|cadetblue|chartreuse|chocolate|coral|cornflowerblue|cornsilk|crimson|cyan|darkblue|darkcyan|darkgoldenrod|darkgray|darkgreen|darkgrey|darkkhaki|darkmagenta|darkolivegreen|darkorange|darkorchid|darkred|darksalmon|darkseagreen|darkslateblue|darkslategray|darkslategrey|darkturquoise|darkviolet|deeppink|deepskyblue|dimgray|dimgrey|dodgerblue|firebrick|floralwhite|forestgreen|fuchsia|gainsboro|ghostwhite|gold|goldenrod|gray|green|grey|honeydew|hotpink|indianred|indigo|ivory|khaki|lavender|lavenderblush|lawngreen|lemonchiffon|lightblue|lightcoral|lightcyan|lightgoldenrodyellow|lightgray|lightgreen|lightgrey|lightpink|lightsalmon|lightseagreen|lightskyblue|lightslategray|lightslategrey|lightsteelblue|lightyellow|lime|limegreen|linen|magenta|maroon|mediumaquamarine|mediumblue|mediumorchid|mediumpurple|mediumseagreen|mediumslateblue|mediumspringgreen|mediumturquoise|mediumvioletred|midnightblue|mintcream|mistyrose|moccasin|navajowhite|navy|oldlace|olive|olivedrab|orange|orangered|orchid|palegoldenrod|palegreen|paleturquoise|palevioletred|papayawhip|peachpuff|peru|pink|plum|powderblue|purple|red|rosybrown|royalblue|saddlebrown|salmon|sandybrown|seagreen|seashell|sienna|silver|skyblue|slateblue|slategray|slategrey|snow|springgreen|steelblue|tan|teal|thistle|tomato|transparent|turquoise|violet|wheat|white|whitesmoke|yellow)/
}

// Clear 'Log' attribute
void clearAttrLog() {
  sendEvent(name: "Log", value: " ", descriptionText: "", isStateChange:false)
}
