/**
 *
 * Energy Cost Calculator
 *
 * Copyright 2022 Ryan Elliott
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * v0.1		RLE		Creation
 * v0.2		RLE		Substantial updates to the UI along with functionality. 
 * v0.3		RLE		Further UI overhauls. Added rate scheduling.
 * v0.3.5	RLE		Squashing bugs around variable types. Fixed an issue where state variables were not being populated until the app was opened the first time after installation.
 * v0.3.6	RLE		Added an option to select which day of the month that reset happens. Moved enum lists to use variable for the options.
 * v0.3.7	RLE		Limited day of month selection to 30 to prevent PICNIC errors.
 * v0.4.0	RLE		Created "Advanced Options" page. 
 					Added user verification prompts for rate costs over 1.
					Added reset and recalculate options.
 * v0.4.1	RLE		Updated language for verification prompts. Fixed rounding for rate display on main page.
 * v0.4.2	RLE		Added more reset options for devices.
 * v0.4.3	RLE		Bug fix for reset menu. Fixed some logging.
 * v0.4.5	RLE		Dynamic hide/unhide sections based on installation status.
					Prep for next update (adding "yesterday" variables for energy use)
 * v0.4.6	RLE		Hotfix for logic to clear input selections from the advanced menu.
 * v0.4.7	RLE		Added option for static charges
 */
 
definition(
    name: "Energy Cost Calculator",
    namespace: "rle",
    author: "Ryan Elliott",
    description: "Creates a table to track the cost of energy meters.",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: ""
)

preferences {
	page(name: "mainPage")
	page(name: "pageSelectVariables")
	page(name: "pageSetRateSchedule")
	page(name: "advancedOptions")
}

def mainPage() {
    
	if(state.energies == null) state.energies = [:]
	if(state.energiesList == null) state.energiesList = []
	if(!state.energyRate) state.energyRate = 0.1
	if(!state.staticCharge) state.staticCharge = 0
	if(!state.finalStaticCharge) state.finalStaticCharge = 0
	if(app.getInstallationState() != "COMPLETE") {hide=false} else {hide=true}

	//Recalulate cost if selected from advanced options
	if(costResetTwo == "Yes") {recalc()} else {app.removeSetting("costResetTwo")}
	//Clear out input options if leftover from advanced options
	if(!confirmationResetTable && !confirmationResetDevice) app.removeSetting("resetOptionZero")
	if((resetOptionZero || deviceResetSelection || deviceOptionReset) && !confirmationResetDevice) {
		app.removeSetting("deviceResetSelection")
		app.removeSetting("deviceOptionReset")
	}
	//Reset entire table if selected from advanced options
	if(confirmationResetTable == "Yes") {nuclear("everything")} else if(confirmationResetTable == "No") {
		app.removeSetting("confirmationResetTable")
		app.removeSetting("resetOptionZero")
		}
	//Reset a particular period for a particular device if selected from advanced options
	if(confirmationResetDevice == "Yes") {nuclear("device")} else if(confirmationResetDevice == "No") {
		app.removeSetting("confirmationResetDevice") 
		app.removeSetting("resetOptionZero")
		app.removeSetting("deviceResetSelection")
		app.removeSetting("deviceOptionReset")
		}


	//Main page
	dynamicPage(name: "mainPage", uninstall: true, install: true) {
		section(getFormat("header","App Name"),hideable: true, hidden: hide) {
            label title: getFormat("important","Enter a name for this app."), required:true, width: 4, submitOnChange: true
        }

		section(getFormat("header","Device Selection"),hideable: true, hidden: hide) {
			if(!hide) paragraph getFormat("important2Bold","All values will start at 0 from the time that a device is added.")
			input "energies", "capability.energyMeter", title: getFormat("important","Select Energy Devices to Measure Cost"), multiple: true, submitOnChange: true, width: 4
			energies.each {dev ->
					if(!state.energies["$dev.id"]) {
						state.energies["$dev.id"] = [todayEnergy: 0, dayStart: dev.currentEnergy ?: 0, lastEnergy: dev.currentEnergy ?: 0, 
							var: "",thisWeekEnergy: 0,thisMonthEnergy: 0,lastWeekEnergy: 0,lastMonthEnergy: 0,todayCost: 0, thisWeekCost: 0, 
							thisMonthCost: 0, yesterdayEnergy: 0]
						state.energiesList += dev.id
					}
				}
		}
		if(app.getInstallationState() == "COMPLETE") {
			section{
				href(name: "hrefSetRateSchedule", title: getFormat("importantBold","Click here to update the rate information"),description: "", page: "pageSetRateSchedule", width:4,newLineAfter:true)
				href(name: "hrefSelectVariables", title: getFormat("importantBold","Click here to add and/or remove variable links"),description: "", page: "pageSelectVariables", width:4)
			}

			section{
				
				if(energies) {
					if(energies.id.sort() != state.energiesList.sort()) { //something was removed
						state.energiesList = energies.id
						Map newState = [:]
						energies.each{d ->  newState["$d.id"] = state.energies["$d.id"]}
						state.energies = newState
					}
					updated()

					newEnergy = state.energyRate
					if(symbol) {rateDisplayFormat = symbol+newEnergy} else {rateDisplayFormat = newEnergy}
					paragraph getFormat("rateDisplay","Current Pricing is ${rateDisplayFormat} per KWH")

					paragraph displayTable()

					input "refresh", "button", title: "Refresh Table", width: 2
					paragraph ""
					
					href(name: "hrefAdvancedOptions", title: getFormat("importantBold","Click here to access advanced options/utilities"),description: "", page: "advancedOptions", width:4,newLineAfter:true)
				}
			}
		} else {
			section(getFormat("header","CLICK DONE TO INSTALL APP AFTER SELECTING DEVICES")) {
				paragraph ""
			}
		}
	}
}

def pageSelectVariables() {
	logTrace "Loading variable table..."
	dynamicPage(name: "pageSelectVariables", uninstall: false, install: false, nextPage: "mainPage") {
		section(getFormat("header","Link the Cost Value to a Hub Variable")) {
			if(energies)
					paragraph getFormat("important","The selected variable MUST be of type \"String\"")
					paragraph displayVariableTable()
			if(state.newTodayVar) {
				logTrace "newTodayVar is ${state.newTodayVar}"
				List vars = getAllGlobalVars().findAll{it.value.type == "string"}.keySet().collect().sort{it.capitalize()}
				input "newVar", "enum", title: "Select Variable", submitOnChange: true, width: 4, options: vars, newLineAfter: true
				if(newVar) {
					addInUseGlobalVar(newVar)
					state.energies[state.newTodayVar].todayVar = newVar
					state.remove("newTodayVar")
					app.removeSetting("newVar")
					paragraph "<script>{changeSubmit(this)}</script>"
				}
			} else if(state.remTodayVar) {
				removeInUseGlobalVar(state.energies[state.remTodayVar].todayVar)
				state.energies[state.remTodayVar].todayVar = ""
				state.remove("remTodayVar")
				paragraph "<script>{changeSubmit(this)}</script>"
			} else if(state.newWeekVar) {
				logTrace "newWeekVar is ${state.newWeekVar}"
				List vars = getAllGlobalVars().findAll{it.value.type == "string"}.keySet().collect().sort{it.capitalize()}
				input "newVar", "enum", title: "Select Variable", submitOnChange: true, width: 4, options: vars, newLineAfter: true
				if(newVar) {
					addInUseGlobalVar(newVar)
					state.energies[state.newWeekVar].weekVar = newVar
					state.remove("newWeekVar")
					app.removeSetting("newVar")
					paragraph "<script>{changeSubmit(this)}</script>"
				}
			} else if(state.remWeekVar) {
				removeInUseGlobalVar(state.energies[state.remWeekVar].weekVar)
				state.energies[state.remWeekVar].weekVar = ""
				state.remove("remWeekVar")
				paragraph "<script>{changeSubmit(this)}</script>"
			} else if(state.newMonthVar) {
				logTrace "newMonthVar is ${state.newMonthVar}"
				List vars = getAllGlobalVars().findAll{it.value.type == "string"}.keySet().collect().sort{it.capitalize()}
				input "newVar", "enum", title: "Select Variable", submitOnChange: true, width: 4, options: vars, newLineAfter: true
				if(newVar) {
					addInUseGlobalVar(newVar)
					state.energies[state.newMonthVar].monthVar = newVar
					state.remove("newMonthVar")
					app.removeSetting("newVar")
					paragraph "<script>{changeSubmit(this)}</script>"
				}
			} else if(state.remMonthVar) {
				removeInUseGlobalVar(state.energies[state.remMonthVar].monthVar)
				state.energies[state.remMonthVar].monthVar = ""
				state.remove("remMonthVar")
				paragraph "<script>{changeSubmit(this)}</script>"
			} else if(state.newTodayTotalVar) {
				List vars = getAllGlobalVars().findAll{it.value.type == "string"}.keySet().collect().sort{it.capitalize()}
				input "newVar", "enum", title: "Select Variable", submitOnChange: true, width: 4, options: vars, newLineAfter: true
				if(newVar) {
					addInUseGlobalVar(newVar)
					state.todayTotalVar = newVar
					state.remove("newTodayTotalVar")
					app.removeSetting("newVar")
					paragraph "<script>{changeSubmit(this)}</script>"
				}
			} else if(state.remTodayTotalVar) {
				removeInUseGlobalVar(state.todayTotalVar)
				state.todayTotalVar = ""
				state.remove("remTodayTotalVar")
				paragraph "<script>{changeSubmit(this)}</script>"
			} else if(state.newWeekTotalVar) {
				List vars = getAllGlobalVars().findAll{it.value.type == "string"}.keySet().collect().sort{it.capitalize()}
				input "newVar", "enum", title: "Select Variable", submitOnChange: true, width: 4, options: vars, newLineAfter: true
				if(newVar) {
					addInUseGlobalVar(newVar)
					state.weekTotalVar = newVar
					state.remove("newWeekTotalVar")
					app.removeSetting("newVar")
					paragraph "<script>{changeSubmit(this)}</script>"
				}
			} else if(state.remWeekTotalVar) {
				removeInUseGlobalVar(state.weekTotalVar)
				state.weekTotalVar = ""
				state.remove("remWeekTotalVar")
				paragraph "<script>{changeSubmit(this)}</script>"
			} else if(state.newMonthTotalVar) {
				List vars = getAllGlobalVars().findAll{it.value.type == "string"}.keySet().collect().sort{it.capitalize()}
				input "newVar", "enum", title: "Select Variable", submitOnChange: true, width: 4, options: vars, newLineAfter: true
				if(newVar) {
					addInUseGlobalVar(newVar)
					state.monthTotalVar = newVar
					state.remove("newMonthTotalVar")
					app.removeSetting("newVar")
					paragraph "<script>{changeSubmit(this)}</script>"
				}
			} else if(state.remMonthTotalVar) {
				removeInUseGlobalVar(state.monthTotalVar)
				state.monthTotalVar = ""
				state.remove("remMonthTotalVar")
				paragraph "<script>{changeSubmit(this)}</script>"
			}
		}
	}
}

def pageSetRateSchedule() {
	
	monthList = ["ALL","JAN","FEB","MAR","APR","MAY","JUN","JUL","AUG","SEP","OCT","NOV","DEC"]
	hoursList = [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23]
	dayList = ["ALL","SUN","MON","TUE","WED","THU","FRI","SAT"]
	newEnergy = state.energyRate
	newStaticChargeDisplay = state.staticCharge ?: 0
	if(symbol) {rateDisplayFormat = symbol+newEnergy; staticChargeDisplay = symbol+newStaticChargeDisplay} else {staticChargeDisplay = newStaticChargeDisplay}

	dynamicPage(name: "pageSetRateSchedule",title:getFormat("header","Configure Rate Options"), uninstall: false, install: false, nextPage: "mainPage") {
		section(getFormat("importantBold","Use a Static Rate or Rate Schedule"),hideable:true,hidden:false) {
			input "scheduleType", "bool", title: getFormat("important2","Disabled: Use a static rate</br>Enabled: Use a rate schedule"), defaultValue: false, displayDuringSetup: false, required: false, width: 4, submitOnChange: true
			}
		if(scheduleType) {
			if(state.schedules == null) state.schedules = [:]
			if(state.schedulesList == null) state.schedulesList = []
			
			section(getFormat("header","Set a dynamic rate schedule below")){
				paragraph getFormat("lessImportant","Current rate is ${rateDisplayFormat} per KWH")
				paragraph displayRateTable()
				logDebug "Schedules are ${state.schedules}"
				if(state.addNewRateSchedule) {
					input "newSchedule", "string", title: "What is the schedule name?", required: false, width: 4, submitOnChange: true, newLineAfter: true
					if(newSchedule) {
						if(!state.schedules["$newSchedule"]) {state.schedules["$newSchedule"] = [rateDayOfWeek: "",rateTimeOfDay: "", rateMonth:"",rateCost:""]}
						state.schedulesList.add(newSchedule)
						state.remove("addNewRateSchedule")
						app.removeSetting("newSchedule")
						logDebug "Schedules are ${state.schedules}"
						paragraph "<script>{changeSubmit(this)}</script>"
					}
				} else if(state.delRateSchedule) {
					input "delSchedule", "enum", title: "Which schedule should be removed?", options: state.schedulesList, submitOnChange: true, newLineAfter: true, width: 4
					if(delSchedule) {
						state.schedulesList.remove(delSchedule)
						state.schedules.remove(delSchedule)
						state.remove("delRateSchedule")
						app.removeSetting("delSchedule")
						paragraph "<script>{changeSubmit(this)}</script>"
					}
				} else if(state.newRateDay) {
					logTrace "newRateDay is ${state.newRateDay}"
					input "rateDayOfWeek", "enum", title: "What days of the week?", options: dayList, multiple:true, width: 4, submitOnChange: true, newLineAfter: true
					if(rateDayOfWeek) {
						if(rateDayOfWeek.contains("ALL")) rateDayOfWeek = "ALL"
						state.schedules[state.newRateDay].rateDayOfWeek = rateDayOfWeek
						state.remove("newRateDay")
						app.removeSetting("rateDayOfWeek")
						paragraph "<script>{changeSubmit(this)}</script>"
					}
				} else if(state.remRateDay) {
					state.schedules[state.remRateDay].rateDayOfWeek = ""
					state.remove("remRateDay")
					paragraph "<script>{changeSubmit(this)}</script>"
				} else if(state.newRateTime) {
					logTrace "newRateTime is ${state.newRateTime}"
					input "rateTimeOfDay", "enum", title: "What is the start time?", options: hoursList, width: 4, submitOnChange: true, newLineAfter: true
					if(rateTimeOfDay) {
						state.schedules[state.newRateTime].rateTimeOfDay = rateTimeOfDay
						state.remove("newRateTime")
						app.removeSetting("rateTimeOfDay")
						paragraph "<script>{changeSubmit(this)}</script>"
					}
				} else if(state.remRateTime) {
					state.schedules[state.remRateTime].rateTimeOfDay = ""
					state.remove("remRateTime")
					paragraph "<script>{changeSubmit(this)}</script>"
				} else if(state.newRateMonths) {
					logTrace "newRateMonths is ${state.newRateMonths}"
					input "rateMonth", "enum", title: "Which months?", options: monthList, multiple:true, width: 4, submitOnChange: true, newLineAfter: true
					if(rateMonth) {
						if(rateMonth.contains("ALL")) rateMonth = "ALL"
						state.schedules[state.newRateMonths].rateMonth = rateMonth
						state.remove("newRateMonths")
						app.removeSetting("rateMonth")
						paragraph "<script>{changeSubmit(this)}</script>"
					}
				} else if(state.remRateMonths) {
					state.schedules[state.remRateMonths].rateMonth = ""
					state.remove("remRateMonths")
					paragraph "<script>{changeSubmit(this)}</script>"
				} else if(state.addRateAmount) {
					logTrace "addRateAmount is ${state.addRateAmount}"
					if(!rateCost) input "rateCost", "string", title: "What is your energy rate per kWh?<br>", required: false, default: "0.1", width: 4, submitOnChange: true
					if(rateCost) {
						String pattern = /(\d*[0-9]\d*(\.\d+)?|0*\.\d*[0-9]\d*)/
						java.util.regex.Matcher matching = rateCost =~ pattern
						newRateCost = matching[0][1].toDouble()
						if(newRateCost > 1) {
							input "newRateCostCheck", "enum", title: getFormat("importantBold","Are you sure <b>*** ${newRateCost} ***</b> is correct?")+getFormat("lessImportant","<br>Entered cost is > 1"), options: ["Yes","No"], required: false, width: 4, submitOnChange: true
							if(newRateCostCheck == "Yes") {
								app.removeSetting("newRateCostCheck")
								state.schedules[state.addRateAmount].rateCost = newRateCost
								state.remove("addRateAmount")
								app.removeSetting("rateCost")
								paragraph "<script>{changeSubmit(this)}</script>"
							} else if(newRateCostCheck == "No") {
								app.removeSetting("newRateCostCheck")
								state.remove("addRateAmount")
								app.removeSetting("rateCost")
								paragraph "<script>{changeSubmit(this)}</script>"
							}
						} else {
							state.schedules[state.addRateAmount].rateCost = newRateCost
							state.remove("addRateAmount")
							app.removeSetting("rateCost")
							paragraph "<script>{changeSubmit(this)}</script>"
						}
					}
				} else if(state.remRateAmount) {
					state.schedules[state.remRateAmount].rateCost = ""
					state.remove("remRateAmount")
					paragraph "<script>{changeSubmit(this)}</script>"
				}
			}
			section(getFormat("importantBold","Manual Override"),hideable:true,hidden:true) {
				input "energyRateOverride", "string", title: getFormat("important","Enter a rate here to manually override the current rate:"),required: false, width: 4, submitOnChange: true
				if(energyRateOverride) {
					String pattern = /(\d*[0-9]\d*(\.\d+)?|0*\.\d*[0-9]\d*)/
					java.util.regex.Matcher matching = energyRateOverride =~ pattern
					energyRateOverride = matching[0][1].toDouble()
					log.warn "Manually overriding current rate of ${state.energyRate} with ${energyRateOverride}"
					state.energyRate = energyRateOverride
					app.removeSetting("energyRateOverride")
				}
			} 
		} else {
			section{
				newEnergy = state.energyRate
				if(symbol) {rateDisplayFormat = symbol+newEnergy} else {rateDisplayFormat = newEnergy}
				paragraph getFormat("lessImportant","Current rate is ${rateDisplayFormat} per KWH")

				if(!energyRate) input "energyRate", "string", title: getFormat("header","What is your energy rate per kWh?"), required: false, default: 1, width: 4, submitOnChange: true
				if(energyRate) {
					String pattern = /(\d*[0-9]\d*(\.\d+)?|0*\.\d*[0-9]\d*)/
					java.util.regex.Matcher matching = energyRate =~ pattern
					newEnergyRate = matching[0][1].toDouble()
					if(newEnergyRate >= 1) {
						input "newRateCostCheck", "enum", title: getFormat("importantBold","Are you sure <b>*** ${newEnergyRate} ***</b> is correct?")+getFormat("lessImportant","<br>Entered cost is > 1"), options: ["Yes","No"], required: false, width: 4, submitOnChange: true
						if(newRateCostCheck == "Yes") {
							app.removeSetting("newRateCostCheck")
							state.energyRate = newEnergyRate
							app.removeSetting("energyRate")
						} else if(newRateCostCheck == "No") {
							app.removeSetting("newRateCostCheck")
							app.removeSetting("energyRate")
							}
						} else {
							state.energyRate = newEnergyRate
							app.removeSetting("energyRate")
							paragraph "<script>{changeSubmit(this)}</script>"
						}
				}
			}
		}
		section(getFormat("importantBold","Set Static Charges"),hideable:true,hidden:false) {
			input "staticChargeFrequency", "bool", title: getFormat("important2","Disabled: Static charge is added daily</br>Enabled: Static charge is added monthly"), defaultValue: false, displayDuringSetup: false, required: false, width: 4, submitOnChange: true
			if(staticChargeFrequency) {staticChargeRecurrence = "MONTHLY"; daysInCurrentMonth = java.time.LocalDate.now().lengthOfMonth()} else if(!staticChargeFrequency) {staticChargeRecurrence = "DAILY"}
			paragraph getFormat("lessImportant","Current static charges are ${staticChargeDisplay} per day")
			input "staticCharge", "string", title: getFormat("important","What are your <b>${staticChargeRecurrence}</b> static charges?"),required: false, width: 4, submitOnChange: true
			if(staticCharge) {
				String pattern = /(\d*[0-9]\d*(\.\d+)?|0*\.\d*[0-9]\d*)/
				java.util.regex.Matcher matching = staticCharge =~ pattern
				newStaticCharge = matching[0][1].toDouble()
				if(newStaticCharge > 1) newStaticCharge = newStaticCharge.toBigDecimal().setScale(2,BigDecimal.ROUND_HALF_UP)
				state.staticCharge = newStaticCharge
				app.removeSetting("staticCharge")
				paragraph "<script>{changeSubmit(this)}</script>"
			}
			if(staticChargeFrequency) {state.finalStaticCharge = state.staticCharge/daysInCurrentMonth} else if(!staticChargeFrequency) {state.finalStaticCharge = state.staticCharge}
		}
		section(getFormat("importantBold","Set the Currency Symbol"),hideable:true,hidden:true) {
			input "symbol", "string", title: getFormat("important","What is your currency symbol?"),required: false, width: 4, submitOnChange: true
		}
	}
}

def advancedOptions() {
	dateList = [1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30]
	resetStartOptions = ["Everything","A Device"]
	
	resetDeviceList = []
	energies.each {dev ->
	resetDeviceList.add(dev.displayName)}
    resetDeviceList.sort()

	deviceOptionList = ["Today","This Week","This Month"]

	dynamicPage(name: "advancedOptions",title:getFormat("header","Advanced Options and Utilities"), uninstall: false, install: false, nextPage: "mainPage") {
		section(getFormat("importantBold","Set the Monthly Reset Day"),hideable:true,hidden:false) {
			input "monthResetDay", "enum", title: getFormat("important","What day of the month should monthly reset happen?")+getFormat("lessImportant","<br>Will be at 00:00:08 on the selected day.<br>No selection will default to the first day of the month."), options: dateList, required: false, width: 4, submitOnChange: true
		}
		section(getFormat("importantBold","Recalculate Cost"),hideable:true,hidden:false) {
			if(!costResetTwo) {
				if(!costResetOne) input "costResetOne", "enum", title: getFormat("important","Recalculate all costs based on the current rate?")+getFormat("lessImportant","<br>Current rate is ${state.energyRate}")+getFormat("red","<br>There's no going back!"), options: ["Yes"], required: false, width: 4, submitOnChange: true
				if(costResetOne) {
					app.removeSetting("costResetOne")
					input "costResetTwo", "enum", title: getFormat("important2Bold","*** Are you sure you want to reset ALL costs? ***")+getFormat("lessImportant","<br>Confirm your selection and click the \"Next\" button.")+getFormat("red","<br>There's no going back!"), options: ["Yes","No"], required: false, width: 4, submitOnChange: false
				}
			}
		}
        section(getFormat("importantBold","Reset Options"),hideable:true,hidden:false) {
            if(!resetOptionZero) input "resetOptionZero", "enum", title: getFormat("important","What type of item do you want to reset?"), options: resetStartOptions, required:false, width:4, submitOnChange:true
            if(resetOptionZero == "Everything") {
                input "confirmationResetTable", "enum", title: getFormat("important2Bold","*** Are you sure you want to reset EVERYTHING? ***")+getFormat("lessImportant","<br>Confirm your selection and click the \"Next\" button.")+getFormat("red","<br>There's no going back once complete!"), options: ["Yes","No"], required: false, width: 4, submitOnChange: false
			} else if(resetOptionZero == "A Device") {
					if(!deviceResetSelection) input "deviceResetSelection", "enum", title: getFormat("important","Which device do you want to reset?")+getFormat("red","<br>There's no going back once complete!"), options: resetDeviceList, required: false, width: 4, submitOnChange: true
					if(deviceResetSelection) {
						if(!deviceOptionReset) input "deviceOptionReset", "enum", title: getFormat("important","Which part of ${deviceResetSelection} do you want to reset?")+getFormat("red","<br>There's no going back once complete!"), options: deviceOptionList, required: false, width: 4, submitOnChange: true
						if(deviceOptionReset) {
							if(!confirmationResetDevice) input "confirmationResetDevice", "enum", title: getFormat("important2Bold","*** Are you sure you want to reset ${deviceOptionReset} for ${deviceResetSelection}? ***")+getFormat("lessImportant","<br>Confirm your selection and click the \"Next\" button.")+getFormat("red","<br>There's no going back once complete!"), options: ["Yes","No"], required: false, width: 4, submitOnChange: false
						}
					}
				}
			}
		section(getFormat("importantBold","Logging Options"),hideable:true,hidden:false) {
			input "debugOutput", "bool", title: "Enable debug logging?", defaultValue: false, displayDuringSetup: false, required: false, width: 2
			input "traceOutput", "bool", title: "Enable trace logging?", defaultValue: false, displayDuringSetup: false, required: false, width: 2
		}
	}
}

String displayTable() {
	logDebug "Table display called"
	String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
		"<thead><tr style='border-bottom:2px solid black'><th style='border-right:2px solid black'>Meter</th>" +
		"<th>Energy Use Today</th>" +
		"<th style='border-right:2px solid black'>Today's Cost</th>" +
		"<th>Energy Use This Week</th>" +
		"<th>Energy Cost This Week</th>" +
		"<th style='border-right:2px solid black'>Energy Use Last Week</th>" +
		"<th>Energy Use This Month</th>" +
		"<th>Energy Cost This Month</th>" +
		"<th>Energy Use Last Month</th></tr></thead>"
	updateDeviceEnergy()
	energies.sort{it.displayName.toLowerCase()}.each {dev ->
		device = state.energies["$dev.id"]

		//Get energy values for each device.
		todayEnergy = device.todayEnergy.toDouble().round(3)
		thisWeekEnergy = device.thisWeekEnergy.toDouble().round(3)
		thisMonthEnergy = device.thisMonthEnergy.toDouble().round(3)
		lastWeekEnergy = device.lastWeekEnergy.toDouble().round(3)
		lastMonthEnergy = device.lastMonthEnergy.toDouble().round(3)

		//Get cost values, round, and add symbol
		todayCost = new BigDecimal(device.todayCost).setScale(2,BigDecimal.ROUND_HALF_UP)
		thisWeekCost = new BigDecimal(device.thisWeekCost).setScale(2,BigDecimal.ROUND_HALF_UP)
		thisMonthCost = new BigDecimal(device.thisMonthCost).setScale(2,BigDecimal.ROUND_HALF_UP)

		if(symbol) {todayCost = symbol+todayCost.toString()}
		if(symbol) {thisWeekCost = symbol+thisWeekCost.toString()}
		if(symbol) {thisMonthCost = symbol+thisMonthCost.toString()}

		//Build display strings
		String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev"
		str += "<tr style='color:black'><td style='border-right:2px solid black'>$devLink</td>" +
			"<td style='color:#be05f5'><b>$todayEnergy</b></td>" +
			"<td style='border-right:2px solid black; color:#be05f5' title='Money spent running ${dev}'><b>$todayCost</b></td>" +
			"<td style='color:#007cbe'><b>$thisWeekEnergy</b></td>" +
			"<td title='Money spent running ${dev}' style='color:#007cbe'><b>$thisWeekCost</b></td>" +
			"<td style='border-right:2px solid black; color:#007cbe'><b>$lastWeekEnergy</b></td>" +
			"<td style='color:#5a8200'><b>$thisMonthEnergy</b></td>" +
			"<td title='Money spent running $dev' style='color:#5a8200'><b>$thisMonthCost</b></td>" +
			"<td style='color:#5a8200'><b>$lastMonthEnergy</b></td></tr>"
	}
	//Get total energy values
	todayTotalEnergy = state.todayTotalEnergy.toDouble().round(3)
	thisWeekTotal = state.thisWeekTotal.toDouble().round(3)
	thisMonthTotal = state.thisMonthTotal.toDouble().round(3)
	lastWeekTotal = state.lastWeekTotal ?: 0 
	lastWeekTotal = lastWeekTotal.toDouble().round(3) ?: 0
	lastMonthTotal = state.lastMonthTotal ?: 0
	lastMonthTotal = lastMonthTotal.toDouble().round(3) ?: 0

	//Get cost values, round, and add symbol
	totalCostToday = new BigDecimal(state.totalCostToday).setScale(2,BigDecimal.ROUND_HALF_UP)
	totalCostWeek = new BigDecimal(state.totalCostWeek).setScale(2,BigDecimal.ROUND_HALF_UP)
	totalCostMonth = new BigDecimal(state.totalCostMonth).setScale(2,BigDecimal.ROUND_HALF_UP)

	if(symbol) {totalCostToday = symbol+totalCostToday.toString()}
	if(symbol) {totalCostWeek = symbol+totalCostWeek.toString()} 
	if(symbol) {totalCostMonth = symbol+totalCostMonth.toString()}

	//Build display string
    str += "<tr style='border-top:2px solid black'><td style='border-right:2px solid black'>Total</td>" +
			"<td style='color:#be05f5'><b>$todayTotalEnergy</b></td>" +
			"<td style='border-right:2px solid black; color:#be05f5' title='Money spent running $dev'><b>$totalCostToday</b></td>" +
			"<td style='color:#007cbe'><b>$thisWeekTotal</b></td>" +
			"<td title='Money spent running $dev' style='color:#007cbe'><b>$totalCostWeek</b></td>" +
			"<td style='border-right:2px solid black; color:#007cbe'><b>$lastWeekTotal</b></td>" +
			"<td style='color:#5a8200'><b>$thisMonthTotal</b></td>" +
			"<td title='Money spent running $dev' style='color:#5a8200'><b>$totalCostMonth</b></td>" +
			"<td style='color:#5a8200'><b>$lastMonthTotal</b></td></tr>"
	str += "</table></div>"
	str
}

String displayVariableTable() {
	logDebug "Variable table display called"
	String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
		"<thead><tr style='border-bottom:2px solid black'><th style='border-right:2px solid black'>Meter</th>" +
		"<th>Today's Cost Variable</th>" +
		"<th>This Week Cost Variable</th>" +
		"<th>This Month Cost Variable</th></tr></thead>"
	energies.sort{it.displayName.toLowerCase()}.each {dev ->
		device = state.energies["$dev.id"]
		String todayVar = device.todayVar
		String weekVar = device.weekVar
		String monthVar = device.monthVar
		String devLink = "<a href='/device/edit/$dev.id' target='_blank' title='Open Device Page for $dev'>$dev"
		String todaysVar = todayVar ? buttonLink("noToday$dev.id", todayVar, "purple") : buttonLink("today$dev.id", "Select", "red")
		String weeksVar = weekVar ? buttonLink("noWeek$dev.id", weekVar, "purple") : buttonLink("week$dev.id", "Select", "red")
		String monthsVar = monthVar ? buttonLink("noMonth$dev.id", monthVar, "purple") : buttonLink("month$dev.id", "Select", "red")
		str += "<tr style='color:black'><td style='border-right:2px solid black'>$devLink</td>" +
		"<td title='${todayVar ? "Deselect $todayVar" : "Set a string hub variable to todays cost value"}'>$todaysVar</td>" +
		"<td title='${weekVar ? "Deselect $weekVar" : "Set a string hub variable to this weeks cost value"}'>$weeksVar</td>" +
		"<td title='${monthVar ? "Deselect $monthVar" : "Set a string hub variable to this months cost value"}'>$monthsVar</td></tr>"
	}
	String todayTotalVar = state.todayTotalVar
	String weekTotalVar = state.weekTotalVar
	String monthTotalVar = state.monthTotalVar
	String todaysTotalVar = todayTotalVar ? buttonLink("noVarTodayTotal", todayTotalVar, "purple") : buttonLink("varTodayTotal", "Select", "red")
	String weeksTotalVar = weekTotalVar ? buttonLink("noVarWeekTotal", weekTotalVar, "purple") : buttonLink("varWeekTotal", "Select", "red")
	String monthsTotalVar = monthTotalVar ? buttonLink("noVarMonthTotal", monthTotalVar, "purple") : buttonLink("varMonthTotal", "Select", "red")
	str += "<tr style='color:black'><td style='border-right:2px solid black'>Totals</td>" +
		"<td title='${todayTotalVar ? "Deselect $todayTotalVar" : "Set a string hub variable to todays cost value"}'>$todaysTotalVar</td>" +
		"<td title='${weekTotalVar ? "Deselect $weekTotalVar" : "Set a string hub variable to this weeks cost value"}'>$weeksTotalVar</td>" +
		"<td title='${monthTotalVar ? "Deselect $monthTotalVar" : "Set a string hub variable to this months cost value"}'>$monthsTotalVar</td></tr>"
	str += "</table></div>"
	str
}

String displayRateTable() {
    def schedules = state.schedulesList
    String str = "<script src='https://code.iconify.design/iconify-icon/1.0.0/iconify-icon.min.js'></script>"
	str += "<style>.mdl-data-table tbody tr:hover{background-color:inherit} .tstat-col td,.tstat-col th { padding:8px 8px;text-align:center;font-size:12px} .tstat-col td {font-size:15px }" +
		"</style><div style='overflow-x:auto'><table class='mdl-data-table tstat-col' style=';border:2px solid black'>" +
		"<thead><tr style='border-bottom:2px solid black'><th style='border-right:2px solid black'><b>Rate Schedule Name</b></th>" +
		"<th><b>Days of the Week</b></th>" +
		"<th><b>Start Time</b></th>" +
		"<th><b>Months</b></th>" +
		"<th><b>Rate Cost</b></th></tr></thead>"
	schedules.each {sched ->
		scheds = state.schedules["$sched"]
		String rateDow = scheds.rateDayOfWeek.toString().replaceAll("[\\s\\[\\]]", "")
		String rateTod = scheds.rateTimeOfDay
		String rateMon = scheds.rateMonth.toString().replaceAll("[\\s\\[\\]]", "")
		String rateC = scheds.rateCost
		String rateDay = rateDow ? buttonLink("noRateDay$sched", rateDow, "purple") : buttonLink("addRateDay$sched", "Select", "red")
		String rateTime = rateTod ? buttonLink("noRateTime$sched", rateTod, "purple") : buttonLink("addRateTime$sched", "Select", "red")
		String rateMonths = rateMon ? buttonLink("noRateMonths$sched", rateMon, "purple") : buttonLink("addRateMonths$sched", "Select", "red")
		String rateAmount = rateC ? buttonLink("noRateAmount$sched", rateC, "purple") : buttonLink("addRateAmount$sched", "Select", "red")
		str += "<tr style='color:#0000EE''><td style='border-right:2px solid black'>$sched</td>" +
            "<td title='${rateDow ? "Click to remove days of week" : "Click to set days of week"}'>$rateDay</td>" +
			"<td title='${rateTod ? "Click to remove start time" : "Click to set start time"}'>$rateTime</td>" +
			"<td title='${rateMon ? "Click to remove months list" : "Click to set months list"}'>$rateMonths</td>" + 
			"<td title='${rateC ? "Click to remove rate cost" : "Click to set rate cost"}'>$rateAmount</td></tr>"
	}
	str += "</table>"
	String addRateSchedule = buttonLink("createRateSchedule", "<iconify-icon icon='mdi:calendar-add'></iconify-icon>", "#660000", "25px")
	String remRateSchedule = buttonLink ("removeRateSchedule", "<iconify-icon icon='mdi:calendar-remove-outline'></iconify-icon", "#660000", "25px")
	str += "<table class='mdl-data-table tstat-col' style=';border:none'><thead><tr>" +
		"<th style='border-bottom:2px solid black;border-right:2px solid black;border-left:2px solid black;width:50px' title='Create a New Rate Schedule'>$addRateSchedule</th>" +
		"<th style='border-bottom:2px solid black;border-right:2px solid black;border-left:2px solid black;width:50px' title='Remove a Rate Schedule'>$remRateSchedule</th>" +
		"<th style='border:none;color:#660000;font-size:1.125rem'><b><i class='he-arrow-left2' style='vertical-align:middle'></i>Click here to add or remove a rate schedule</b></th>" +
		"</tr></thead></table>"
    str += "</div>"
    
    return str
}

String buttonLink(String btnName, String linkText, color = "#1A77C9", font = "15px") {
	"<div class='form-group'><input type='hidden' name='${btnName}.type' value='button'></div><div><div class='submitOnChange' onclick='buttonClick(this)' style='color:$color;cursor:pointer;font-size:$font'>$linkText</div></div><input type='hidden' name='settings[$btnName]' value=''>"
}

void appButtonHandler(btn) {
	logDebug "btn is ${btn}"
	if(btn == "varTodayTotal") state.newTodayTotalVar = btn
	else if(btn == "noVarTodayTotal") state.remTodayTotalVar = btn
    else if(btn == "varWeekTotal") state.newWeekTotalVar = btn
	else if(btn == "noVarWeekTotal") state.remWeekTotalVar = btn
    else if(btn == "varMonthTotal") state.newMonthTotalVar = btn
	else if(btn == "noVarMonthTotal") state.remMonthTotalVar = btn
	else if(btn.startsWith("today")) state.newTodayVar = btn.minus("today")
	else if(btn.startsWith("noToday")) state.remTodayVar = btn.minus("noToday")
    else if(btn.startsWith("week")) state.newWeekVar = btn.minus("week")
	else if(btn.startsWith("noWeek")) state.remWeekVar = btn.minus("noWeek")
    else if(btn.startsWith("month")) state.newMonthVar = btn.minus("month")
	else if(btn.startsWith("noMonth")) state.remMonthVar = btn.minus("noMonth")
	else if(btn == "createRateSchedule") state.addNewRateSchedule = btn
	else if(btn == "removeRateSchedule") state.delRateSchedule = btn
	else if(btn.startsWith("noRateDay")) state.remRateDay = btn.minus("noRateDay")
	else if(btn.startsWith("addRateDay")) state.newRateDay = btn.minus("addRateDay")
	else if(btn.startsWith("noRateMonths")) state.remRateMonths = btn.minus("noRateMonths")
	else if(btn.startsWith("addRateMonths")) state.newRateMonths = btn.minus("addRateMonths")
	else if(btn.startsWith("noRateTime")) state.remRateTime = btn.minus("noRateTime")
	else if(btn.startsWith("addRateTime")) state.newRateTime = btn.minus("addRateTime")
	else if(btn.startsWith("noRateAmount")) state.remRateAmount = btn.minus("noRateAmount")
	else if(btn.startsWith("addRateAmount")) state.addRateAmount = btn.minus("addRateAmount")
}

void updated() {
	logTrace "Updated app"
	unsubscribe()
	unschedule()
	initialize()
}

void installed() {
	log.warn "Installed app"
	state.onceReset = 2
	initialize()
}

void uninstalled() {
	log.warn "Uninstalling app"
	removeAllInUseGlobalVar()
}

void initialize() {
	logTrace "Initialized app"
	if(scheduleType) rateScheduler()
	schedule("11 0 0 * * ?",resetDaily)
	schedule("7 0 0 ? * SUN *",resetWeekly)
	if(monthResetDay) {
		schedule("8 0 0 ${monthResetDay} * ? *",resetMonthly)
		} else {
			schedule("8 0 0 1 * ? *",resetMonthly)
			}
	subscribe(energies, "energy", energyHandler)
	resetForTest()
}

void energyHandler(evt) {
    logDebug "Energy change for ${evt.device}"
	updateDeviceEnergy()
}

void updateDeviceEnergy() {
	logDebug "Start energy update"
	def todayTotalEnergy = 0
	def thisWeekTotal = 0
	def thisMonthTotal = 0
	energies.each {dev ->
		device = state.energies["$dev.id"]

		String thisVar = device.var ?: ""
		start = device.dayStart ?: 0
		logTrace "${dev} day start is ${device.dayStart}"
		thisWeek = device.thisWeekEnergy ?: 0
		logTrace "${dev} thisWeekEnergy is ${device.thisWeekEnergy}"
		thisWeekStart = device.weekStart ?: 0
		logTrace "${dev} weekStart is ${device.weekStart}"
		thisMonth = device.thisMonthEnergy ?: 0
		// logTrace "${dev} thisMonthEnergy is ${device.thisMonthEnergy}"
		thisMonthStart = device.monthStart ?: 0
		// logTrace "${dev} monthStart is ${device.monthStart}"
		currentEnergy = dev.currentEnergy ?: 0
		logTrace "${dev} currentEnergy is ${dev.currentEnergy}"

		energyCheck = currentEnergy - start
		logTrace "${dev} energyCheck is ${energyCheck}"
		if(energyCheck < 0) {
			log.info "Energy for ${dev} is less than day start; energy was reset; setting day start and last energy to 0"
			device.dayStart = 0
			device.lastEnergy = 0
			todayEnergy = currentEnergy - device.dayStart
		} else {todayEnergy = energyCheck}
		logTrace "${dev} energy today is ${todayEnergy}"
		
		lastEnergy = device.lastEnergy
		logTrace "${dev} lastEnergy is ${lastEnergy}"
		if (lastEnergy != currentEnergy) {
			logTrace "${dev} changed from ${lastEnergy} to ${currentEnergy}"
		}
		energyChange = currentEnergy - lastEnergy
		logTrace "Energy change for ${dev} is ${energyChange}"

		device.lastEnergy = currentEnergy
		device.energyChange = energyChange
		device.todayEnergy = todayEnergy

		todayTotalEnergy += todayEnergy
		thisWeek = thisWeekStart + todayEnergy
		thisWeekTotal += thisWeek
		device.thisWeekEnergy = thisWeek
		thisMonth = thisMonthStart + todayEnergy
		thisMonthTotal += thisMonth
		device.thisMonthEnergy = thisMonth
	}
	state.todayTotalEnergy = todayTotalEnergy
	state.thisWeekTotal = thisWeekTotal
	state.thisMonthTotal = thisMonthTotal
	logDebug "Energy update done"
	updateCost()
}

void updateCost() {
	logDebug "Start cost update"
	def totalCostToday = 0
	def totalCostWeek = 0
	def totalCostMonth = 0
	tempRate = state.energyRate
	logTrace "Current rate is ${tempRate}"
	energies.each {dev ->
		device = state.energies["$dev.id"]

		tempTodayCost = device.todayCost
		tempWeekCost = device.thisWeekCost
		tempMonthCost = device.thisMonthCost
		
		thisWeek = device.thisWeekEnergy
		thisMonth = device.thisMonthEnergy

		tempEnergy = device.energyChange
		
		costCheck = (tempEnergy*tempRate)
		if(costCheck >= 0) {
			tempCost = costCheck
			} else {
				tempCost = 0
				log.info "Cost change for ${dev} is a negative; energy was reset"}
		if(tempCost > 0) {
			logTrace "Price change for ${dev} is ${tempEnergy} * ${tempRate} = ${tempCost}"
		}
		tempTodayCost += tempCost
		tempWeekCost += tempCost
		tempMonthCost += tempCost
		totalCostToday += tempTodayCost
		totalCostWeek += tempWeekCost
		totalCostMonth += tempMonthCost
		device.todayCost = tempTodayCost
		device.thisWeekCost = tempWeekCost 
		device.thisMonthCost = tempMonthCost 
		if(tempCost > 0) {
			logTrace "New cost for ${dev.displayName} is ${tempTodayCost}"
		}
		//Get and update hub variables for devices
		todayVar = device.todayVar
		weekVar = device.weekVar
		monthVar = device.monthVar
		if(todayVar) {
			tempTodayCost = new BigDecimal(tempTodayCost).setScale(2,BigDecimal.ROUND_HALF_UP)
			if(symbol) {setGlobalVar(todayVar, symbol+tempTodayCost.toString())} else {setGlobalVar(todayVar,tempTodayCost.toString())}
		}
		if(weekVar) {
			tempWeekCost = new BigDecimal(tempWeekCost).setScale(2,BigDecimal.ROUND_HALF_UP)
			if(symbol) {setGlobalVar(weekVar, symbol+tempWeekCost.toString())} else {setGlobalVar(weekVar,tempWeekCost.toString())}
		}
		if(monthVar) {
			tempMonthCost = new BigDecimal(tempMonthCost).setScale(2,BigDecimal.ROUND_HALF_UP)
			if(symbol) {setGlobalVar(monthVar, symbol+tempMonthCost.toString())} else {setGlobalVar(monthVar,tempMonthCost.toString())}
		}
	}
	state.totalCostToday = totalCostToday + state.finalStaticCharge
	state.totalCostWeek = totalCostWeek
	state.totalCostMonth = totalCostMonth

	//Get and update hub variables for 'totals'
	todayTotalVar = state.todayTotalVar
	weekTotalVar = state.weekTotalVar
	monthTotalVar = state.monthTotalVar

	if(todayTotalVar) {
		totalCostToday = new BigDecimal(totalCostToday).setScale(2,BigDecimal.ROUND_HALF_UP)
		if(symbol) {setGlobalVar(todayTotalVar, symbol+totalCostToday.toString())} else {setGlobalVar(todayTotalVar,totalCostToday.toString())}
	}
	if(weekTotalVar) {
		totalCostWeek = new BigDecimal(totalCostWeek).setScale(2,BigDecimal.ROUND_HALF_UP)
		if(symbol) {setGlobalVar(weekTotalVar, symbol+totalCostWeek.toString())} else {setGlobalVar(weekTotalVar,totalCostWeek.toString())}
	}
	if(monthTotalVar) {
		totalCostMonth = new BigDecimal(totalCostMonth).setScale(2,BigDecimal.ROUND_HALF_UP)
		if(symbol) {setGlobalVar(monthTotalVar, symbol+totalCostMonth.toString())} else {setGlobalVar(monthTotalVar,totalCostMonth.toString())}
	}
	logDebug "Cost update done"
}

void rateScheduler() {
	def schedules = state.schedulesList
	schedules.each {sched ->
	scheds = state.schedules["$sched"]
	String rateDow = scheds.rateDayOfWeek.toString().replace("[","").replace("]","").replace(" ","") ?: "*"
	if(rateDow == "ALL") rateDow = "*"
	String rateTod = scheds.rateTimeOfDay ?: "*"
	String rateMon = scheds.rateMonth.toString().replace("[","").replace("]","").replace(" ","") ?: "*"
	if(rateMon == "ALL") rateMon = "*"
	String makingCron = "1 0 ${rateTod} ? ${rateMon} ${rateDow} *"
	logTrace "${sched} cron is ${makingCron}"
	schedule(makingCron,setRate, [data:sched,overwrite:false])
	}
}

void setRate(data) {
	logTrace "Old rate is ${state.energyRate}"
	state.energyRate = state.schedules["$data"].rateCost ?: 1
	logTrace "New rate is ${state.energyRate}"
}

void resetDaily() {
	logDebug "Daily reset"
	state.yesterdayTotal = state.todayTotalEnergy
	energies.each {dev ->
		device = state.energies["$dev.id"]
		device.yesterdayEnergy = device.todayEnergy
		device.dayStart = dev.currentEnergy ?: 0
		device.todayCost = 0
		device.weekStart = device.thisWeekEnergy
		device.monthStart = device.thisMonthEnergy
		logTrace "${dev} starting energy is ${device.dayStart}"
	}
}

void resetWeekly() {
	logDebug "Weekly reset"
	energies.each {dev ->
	device = state.energies["$dev.id"]
	device.lastWeekEnergy = 0
	device.lastWeekEnergy = device.thisWeekEnergy
	device.thisWeekEnergy = 0
	device.thisWeekCost = 0
	}
	state.lastWeekTotal = state.thisWeekTotal
	state.thisWeekTotal = 0
}

void resetMonthly() {
	logDebug "Monthly reset"
	energies.each {dev ->
	device = state.energies["$dev.id"]
	device.lastMonthEnergy = 0
	device.lastMonthEnergy = device.thisMonthEnergy
	device.thisMonthEnergy = 0
	device.thisMonthCost = 0
	}
	state.lastMonthTotal = state.thisMonthTotal
	state.thisMonthTotal = 0
}

def resetForTest(yes) {
	toReset = yes ?: state.onceReset
	logTrace "state.onceReset is ${toReset}"
	String pattern = /.*?(\d+\.\d+).*/
	if(toReset != 2) {
		log.warn "Converting cost variables to decimal"
		tempRate = new BigDecimal(state.energyRate)
		energies.each {dev ->
			device = state.energies["$dev.id"]
			todayCost = device.todayCost
			thisWeekCost = device.thisWeekCost
			thisMonthCost = device.thisMonthCost
			
			
			java.util.regex.Matcher matching = todayCost =~ pattern
			newTodayCost = matching[0][1]
			log.warn "${dev} converted today value of ${newTodayCost}"
			device.todayCost = newTodayCost

			java.util.regex.Matcher matchingWeek = thisWeekCost =~ pattern
			newThisWeekCost = matchingWeek[0][1]
			log.warn "${dev} converted this week value of ${newThisWeekCost}"
			device.thisWeekCost = newThisWeekCost

			java.util.regex.Matcher matchingMonth = thisMonthCost =~ pattern
			newthisMonthCost = matchingMonth[0][1]
			log.warn "${dev} converted this month value of ${newthisMonthCost}"
			device.thisMonthCost = newthisMonthCost


		}
		toReset = 2
		logTrace "state.onceReset updated to ${toReset}"
		state.onceReset = toReset
    } else if(toReset == 2) {
	logTrace "Once reset skipped"
    }
}

def nuclear(where) {
	logTrace "Where is ${where}"
	if(where == "everything") {
		energies.each {dev ->
		state.energies["$dev.id"] = [todayEnergy: 0, dayStart: dev.currentEnergy ?: 0, lastEnergy: dev.currentEnergy ?: 0, var: "",thisWeekEnergy: 0,thisMonthEnergy: 0,lastWeekEnergy: 0,lastMonthEnergy: 0,todayCost: 0, thisWeekCost: 0, thisMonthCost: 0]
		}
		log.warn "It's all gone...I hope you're happy."
		app.removeSetting("confirmationResetTable")
		app.removeSetting("resetOptionZero")
	} else if(where == "device") {
		idResetDevice = 0
		energies.each {it -> if(it.displayName.contains("${deviceResetSelection}")) idResetDevice = it.id }
		energies.each {it -> if(it.displayName.contains("${deviceResetSelection}")) currentEnergy = it.currentEnergy }
		logTrace "Device ID is ${idResetDevice} with current energy of ${currentEnergy}"
		deviceForReset = state.energies["$idResetDevice"]
		switch(deviceOptionReset) {
			case "Today":
				log.warn "Resetting today's values for ${deviceResetSelection}"
				deviceForReset.todayEnergy = 0
				deviceForReset.todayCost = 0
				deviceForReset.dayStart = currentEnergy
				break;
			case "This Week":
				log.warn "Resetting this week's values for ${deviceResetSelection}"
				deviceForReset.thisWeekEnergy = 0
				deviceForReset.thisWeekCost = 0
				deviceForReset.weekStart = 0
				break;
			case "This Month":
				log.warn "Resetting this month's values for ${deviceResetSelection}"
				deviceForReset.thisMonthEnergy = 0
				deviceForReset.thisMonthCost = 0
				deviceForReset.monthStart = 0
				break;
		}
		app.removeSetting("resetOptionZero")
		app.removeSetting("deviceResetSelection")
		app.removeSetting("deviceOptionReset")
		app.removeSetting("confirmationResetDevice")
	}
}

def recalc() {
	tempRate = state.energyRate
	energies.each {dev ->
		device = state.energies["$dev.id"]

		todayEnergy = device.todayEnergy
		thisWeek = device.thisWeekEnergy
		thisMonth = device.thisMonthEnergy

		todayCost = todayEnergy*tempRate
		thisWeekCost = thisWeek*tempRate
		thisMonthCost = thisMonth*tempRate

		device.todayCost = todayCost
		device.thisWeekCost = thisWeekCost 
		device.thisMonthCost = thisMonthCost 
	}

	todayTotalEnergy = state.todayTotalEnergy
	thisWeekTotal = state.thisWeekTotal
	thisMonthTotal = state.thisMonthTotal

	totalCostToday = todayTotalEnergy*tempRate
	totalCostWeek = thisWeekTotal*tempRate
	totalCostMonth = thisMonthTotal*tempRate

	state.totalCostToday = totalCostToday
	state.totalCostWeek = totalCostWeek
	state.totalCostMonth = totalCostMonth
	app.removeSetting("costResetTwo")
}

def getFormat(type, myText="") {
	if(type == "header") return "<div style='color:#660000;font-weight: bold'>${myText}</div>"
	if(type == "red") return "<div style='color:#660000'>${myText}</div>"
	if(type == "importantBold") return "<div style='color:#32a4be;font-weight: bold'>${myText}</div>"
	if(type == "important") return "<div style='color:#32a4be'>${myText}</div>"
	if(type == "important2") return "<div style='color:#5a8200'>${myText}</div>"
	if(type == "important2Bold") return "<div style='color:#5a8200;font-weight: bold'>${myText}</div>"
	if(type == "lessImportant") return "<div style='color:green'>${myText}</div>"
	if(type == "rateDisplay") return "<div style='color:green; text-align: center;font-weight: bold'>${myText}</div>"
}

def logDebug(msg) {
    if (settings?.debugOutput) {
		log.debug msg
    }
}

def logTrace(msg) {
    if (settings?.traceOutput) {
		log.trace msg
    }
}

def logsOff(){
    log.warn "debug logging disabled..."
    app.updateSetting("debugOutput",[value:"false",type:"bool"])
}