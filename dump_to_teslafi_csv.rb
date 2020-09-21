#!/usr/bin/ruby
require 'json'
require 'csv'

class Runner
  def initialize
    @data_id = 0
    @writers = {}
    @fields = [
      "data_id","Date","calendar_enabled","remote_start_enabled","vehicle_id","display_name","color","backseat_token","notifications_enabled","vin","backseat_token_updated_at","id","tokens","id_s","state","user_charge_enable_request","time_to_full_charge","charge_current_request","charge_enable_request","charge_to_max_range","charger_phases","battery_heater_on","managed_charging_start_time","battery_range","charger_power","charge_limit_soc","charger_pilot_current","charge_port_latch","battery_current","charger_actual_current","scheduled_charging_pending","fast_charger_type","usable_battery_level","motorized_charge_port","charge_limit_soc_std","not_enough_power_to_heat","battery_level","charge_energy_added","charge_port_door_open","max_range_charge_counter","charge_limit_soc_max","ideal_battery_range","managed_charging_active","charging_state","fast_charger_present","trip_charging","managed_charging_user_canceled","scheduled_charging_start_time","est_battery_range","charge_rate","charger_voltage","charge_current_request_max","eu_vehicle","charge_miles_added_ideal","charge_limit_soc_min","charge_miles_added_rated","inside_temp","longitude","heading","gps_as_of","latitude","speed","shift_state","seat_heater_rear_right","seat_heater_rear_left_back","seat_heater_left","passenger_temp_setting","is_auto_conditioning_on","driver_temp_setting","outside_temp","seat_heater_rear_center","is_rear_defroster_on","seat_heater_rear_right_back","smart_preconditioning","seat_heater_right","fan_status","is_front_defroster_on","seat_heater_rear_left","gui_charge_rate_units","gui_24_hour_time","gui_temperature_units","gui_range_display","gui_distance_units","sun_roof_installed","rhd","remote_start_supported","homelink_nearby","parsed_calendar_supported","spoiler_type","ft","odometer","remote_start","pr","has_spoiler","roof_color","perf_config","valet_mode","calendar_supported","pf","sun_roof_percent_open","third_row_seats","seat_type","api_version","rear_seat_heaters","rt","exterior_color","df","autopark_state","sun_roof_state","notifications_supported","vehicle_name","dr","autopark_style","car_type","wheel_type","locked","center_display_state","last_autopark_error","car_version","dark_rims","autopark_state_v2","inside_tempF","driver_temp_settingF","outside_tempF","odometerF","idleNumber","sleepNumber","driveNumber","chargeNumber","polling","idleTime","running","rerunning","maxRange","left_temp_direction","max_avail_temp","is_climate_on","right_temp_direction","min_avail_temp","rear_seat_type","power","steering_wheel_heater","wiper_blade_heater","side_mirror_heaters","elevation"
    ]
  end

  def resolve_field(row, field_name)
    if field_name == 'data_id'
      @data_id += 1
      return @data_id
    elsif field_name == 'Date'
      return row['timestamp'].gsub(/\.\d+/, "")
    elsif field_name == 'calendar_enabled'
      return false
    elsif field_name == 'vehicle_id'
      return '27026225181394098'
    elsif field_name == 'display_name'
      return 'Warp'
    elsif field_name == 'color'
      return nil
    elsif field_name == 'backseat_token'
      return nil
    elsif field_name == 'backseat_token_updated_at'
      return nil
    elsif field_name == 'notifications_enabled'
      return true
    elsif field_name == 'vin'
      return '5YJ3E1EB2JF056295'
    elsif field_name == 'id' or field_name == 'id_s'
      return 0
    elsif field_name == 'tokens'
      return ''
    elsif field_name == 'state'
      return row['state']
    elsif field_name == 'battery_current'
      return 0.0
    elsif field_name == 'motorized_charge_port'
      return true
    elsif field_name == 'eu_vehicle'
      return false
    elsif field_name == 'inside_temp'
      return 0.0
    elsif ['seat_heater_rear_left', 'seat_heater_rear_right', 'seat_heater_rear_right_back', 'seat_heater_rear_center', 'seat_heater_rear_left_back', 'seat_heater_left', 'seat_heater_right'].include? field_name
      return 0.0
    elsif field_name == 'driver_temp_setting'
      return 0.0
    elsif field_name == 'driver_temp_settingF'
      return 0.0
    elsif field_name == 'passenger_temp_setting'
      return 0.0
    elsif field_name == 'seat_heater_right'
      return false
    elsif field_name == 'is_auto_conditioning_on'
      return false
    elsif field_name == 'outside_temp'
      return 0.0
    elsif field_name == 'outside_tempF'
      return 0.0
    elsif field_name == 'is_rear_defroster_on'
      return false
    elsif field_name == 'is_front_defroster_on'
      return false
    elsif field_name == 'smart_preconditioning'
      return false
    elsif field_name == 'fan_status'
      return 0
    elsif field_name == 'gui_charge_rate_units'
      return 'mi/hr'
    elsif field_name == 'gui_24_hour_time'
      return false
    elsif field_name == 'gui_temperature_units'
      return 'C'
    elsif field_name == 'gui_range_display'
      return 'Rated'
    elsif field_name == 'gui_distance_units'
      return 'mi/hr'
    elsif field_name == 'sun_roof_installed'
      return '0'
    elsif field_name == 'rhd'
      return false
    elsif field_name == 'spoiler_type'
      return 'Passive'
    elsif field_name == 'has_spoiler'
      return nil
    elsif field_name == 'roof_color'
      return nil
    elsif field_name == 'perf_config'
      return 'P1'
    elsif field_name == 'third_row_seats'
      return '<invalid>'
    elsif field_name == 'seat_type'
      return '1'
    elsif field_name == 'rear_seat_heaters'
      return '3'
    elsif field_name == 'exterior_color'
      return 'RedMulticoat'
    elsif ['autopark_state', 'autopark_state_v2', 'autopark_state_v3'].include? field_name
      return 'ready'
    elsif field_name == 'car_type'
      return 'model3'
    elsif field_name == 'wheel_type'
      return 'Stiletto20'
    elsif field_name == 'dark_rims'
      return nil
    elsif field_name == 'inside_tempF'
      return 0.0
    elsif field_name == 'odometerF'
      return ''
    elsif field_name == 'idleNumber'
      return 0
    elsif field_name == 'sleepNumber'
      return 0
    elsif field_name == 'driveNumber'
      return 0
    elsif field_name == 'chargeNumber'
      return 0
    elsif field_name == 'polling'
      return ''
    elsif field_name == 'idleTime'
      return 0
    elsif field_name == 'running'
      return 0
    elsif field_name == 'rerunning'
      return 0
    elsif field_name == 'maxRange'
      return 310
    elsif field_name == 'left_temp_direction'
      return ''
    elsif field_name == 'right_temp_direction'
      return ''
    elsif field_name == 'max_avail_temp'
      return ''
    elsif field_name == 'min_avail_temp'
      return ''
    elsif field_name == 'is_climate_on'
      return true
    elsif field_name == 'rear_seat_type'
      return nil
    elsif field_name == 'steering_wheel_heater'
      return false
    elsif field_name == 'wiper_blade_heater'
      return false
    elsif field_name == 'side_mirror_heaters'
      return false
    elsif field_name == 'elevation'
      return ''
    elsif field_name == 'charging_state'
      return row["full_data"]['charging_state']['charging_state']
    elsif field_name == 'sun_roof_percent_open'
      return nil
    elsif field_name == 'sun_roof_state'
      return nil
    else
      if row.key? field_name
        row[field_name]
      else
        row.keys.each do |key|
          if row[key].is_a? Hash
            begin
              possible_val = resolve_field(row[key], field_name)
              return possible_val
            rescue
            end
          end
        end
        raise "Unable to resolve field: #{field_name}"
      end
    end
  end

  def get_or_new_writer(shard)
    if @writers[shard]
      @writers[shard]
    else
      @writers[shard] = CSV.new(File.open("out/TeslaFi#{shard}.csv", 'w'))
      @writers[shard] << @fields
    end
  end

  def run
    src = JSON.parse(File.read('metrics_202009202051.json'))["metrics"]

    src.each do |row|
      row["full_data"] = JSON.parse(row["full_data"])

      begin
        to_write = @fields.map { |field| resolve_field(row, field) }.map { |val|
          if val.nil?
            "None"
          elsif val == false
            "False"
          elsif val == true
            "True"
          else
            val
          end
        }
        y, m = to_write[1].scan(/(\d+)-(\d+)/).flatten.map(&:to_i)
        shard = "#{m}#{y}"
        get_or_new_writer(shard) << to_write
        if to_write[0] % 1000 == 0
          puts to_write.inspect
        end
      rescue => e
        puts e
        puts e.backtrace
        puts row.inspect
        exit
      end
    end
    puts "ALL DONE!"
  end
end
Runner.new.run
